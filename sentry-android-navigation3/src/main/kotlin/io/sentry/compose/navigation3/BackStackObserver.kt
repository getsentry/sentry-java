package io.sentry.compose.navigation3

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.PropagationContext
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryLevel.INFO
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.protocol.App
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.ExceptionUtils
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion

/**
 * Observes the back stack managed by a single [SentryNavEffect] and records Sentry state as the
 * back stack is updated.
 *
 * **Warning!** This class is not thread-safe. Clients should serialize calls to
 * [onBackStackChanged] and [cleanup] (e.g., via invocation from an `*Effect` or another form of
 * thread confinement).
 */
internal class BackStackObserver<T : Any>(
  private val scopes: IScopes,
  private val options: SentryNavOptions,
  private val resolvers: () -> RouteResolvers<T>,
) {

  private val routeTranslator =
    RouteTranslator(resolvers, options.maxCapturedBackStackEntries, scopes.options.logger)

  private var previousSnapshot: RouteTranslator.NavigationSnapshot<T>? = null
  private val screenTracker = ScreenTracker()

  private val areNavigationTransactionsEnabled: Boolean
    get() = scopes.options.isTracingEnabled && options.enableNavigationTransactions

  private val navTransactions = NavTransactionManager(scopes, NAVIGATION_OP, TRANSACTION_ORIGIN)

  init {
    addIntegrationToSdkVersion("ComposeNavigation3")
  }

  internal companion object {

    private const val NAVIGATION_CONTEXT_KEY = "navigation"
    private const val NAVIGATION_OP: String = "navigation"
    private const val TRANSACTION_ORIGIN = "auto.navigation.nav3"

    init {
      SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-android-navigation3", BuildConfig.VERSION_NAME)
    }
  }

  /**
   * Updates recorded Sentry data based on the provided [backStack].
   *
   * Note: This method is **not** idempotent. Callers should protect against repeat invocations with
   * the same back stack.
   */
  internal fun onBackStackChanged(backStack: List<T>) {
    val updateWarningState = RouteTranslator.UpdateWarningState()

    guard("onBackStackChanged") {
      val snapshot = routeTranslator.snapshot(backStack, updateWarningState)

      scopes.configureScope { scope ->
        // Always update the recorded backstack, as any of its entries may have changed.
        scope.updateNavigationContext(snapshot, options)

        // Return early if there's nowhere to go or if the top of the back stack hasn't changed...
        val previousTop = previousSnapshot?.top
        val currentTop = snapshot.top

        if (currentTop == null) {
          handleEmptyBackStack(scope)
          previousSnapshot = snapshot
          return@configureScope
        }
        if (previousTop?.entry === currentTop.entry) {
          previousSnapshot = snapshot
          return@configureScope
        }

        // ...otherwise record data for the new nav destination.
        handleNewTop(scope, previousTop, currentTop, snapshot)
        previousSnapshot = snapshot
      }
    }
  }

  internal fun cleanup() {
    previousSnapshot = null

    scopes.configureScope { scope ->
      navTransactions.stop(scope)
      screenTracker.clear(scope)

      if (options.captureBackStack) {
        // This observer owns the Nav3 navigation context while it's in the composition, and cleanup
        // removes it to avoid leaking stale back stack data after observation stops. If the host
        // app replaces one observer with another, there may be a brief gap where events lack
        // navigation context. Apps should keep the observer at the nav root so cleanup only runs
        // when the navigation session is ending, not during normal destination changes.
        scope.removeContexts(NAVIGATION_CONTEXT_KEY)
      }
    }
  }

  private fun handleNewTop(
    scope: IScope,
    previousTop: RouteTranslator.DestinationSnapshot<T>?,
    currentTop: RouteTranslator.DestinationSnapshot<T>,
    snapshot: RouteTranslator.NavigationSnapshot<T>,
  ) {
    if (scopes.options.isEnableScreenTracking) {
      screenTracker.track(scope, currentTop.routeName)
    }

    if (options.enableNavigationBreadcrumbs) {
      scopes.addNav3Breadcrumb(previousTop, currentTop)
    }

    navTransactions.stop(scope)

    if (areNavigationTransactionsEnabled) {
      navTransactions.start(currentTop.routeName, currentTop.arguments) { transaction ->
        transaction.snapshotTrackedNavigationContext(snapshot)
      }
    } else {
      scope.rotatePropagationContext()
    }
  }

  private fun handleEmptyBackStack(scope: IScope) {
    navTransactions.stop(scope)
    screenTracker.clear(scope)
    previousSnapshot = null
  }

  private fun IScope.updateNavigationContext(
    snapshot: RouteTranslator.NavigationSnapshot<T>,
    options: SentryNavOptions,
  ) {
    if (!options.captureBackStack) {
      this.removeContexts(NAVIGATION_CONTEXT_KEY)
      return
    }

    val entries = snapshot.backStackEntries
    if (entries.isEmpty()) {
      this.removeContexts(NAVIGATION_CONTEXT_KEY)
    } else {
      this.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("backstack" to entries))
    }
  }

  /**
   * Snapshots the current route and back stack onto the transaction itself so late-finishing child
   * spans cannot cause the event to inherit newer scope state from a later navigation update.
   */
  private fun ITransaction.snapshotTrackedNavigationContext(
    snapshot: RouteTranslator.NavigationSnapshot<T>
  ) {
    val appContext = contexts.app ?: App().also { contexts.setApp(it) }
    appContext.viewNames = listOf(snapshot.top?.routeName ?: return)

    if (options.captureBackStack && snapshot.backStackEntries.isNotEmpty()) {
      setContext(NAVIGATION_CONTEXT_KEY, mapOf("backstack" to snapshot.backStackEntries))
    }
  }

  private fun IScope.rotatePropagationContext() {
    withPropagationContext { setPropagationContext(PropagationContext()) }
  }

  private fun IScopes.addNav3Breadcrumb(
    from: RouteTranslator.DestinationSnapshot<T>?,
    to: RouteTranslator.DestinationSnapshot<T>,
  ) {
    val breadcrumb =
      Breadcrumb().apply {
        type = NAVIGATION_OP
        category = NAVIGATION_OP

        from?.let {
          data["from"] = it.routeName
          if (it.arguments.isNotEmpty()) {
            data["from_arguments"] = it.arguments
          }
        }

        data["to"] = to.routeName
        if (to.arguments.isNotEmpty()) {
          data["to_arguments"] = to.arguments
        }

        level = INFO
      }

    val hint = Hint()
    hint.set(TypeCheckHint.NAV3_DESTINATION, to.entry)
    this.addBreadcrumb(breadcrumb, hint)
  }

  @Suppress("TooGenericExceptionCaught")
  private inline fun guard(operation: String, body: () -> Unit) {
    try {
      body()
    } catch (t: Throwable) {
      ExceptionUtils.rethrowIfFatal(t)
      scopes.options.logger.log(
        ERROR,
        t,
        "Nav3 instrumentation failed during %s. Skipping this navigation update.",
        operation,
      )
    }
  }
}

private class ScreenTracker {

  private var lastScreenRouteName: String? = null

  /** Tracks the provided [routeName] as the current visible screen. */
  fun track(scope: IScope, routeName: String) {
    scope.screen = routeName
    val app = scope.contexts.app ?: App().also { scope.contexts.setApp(it) }
    app.viewNames = listOf(routeName)
    lastScreenRouteName = routeName
  }

  fun clear(scope: IScope) {
    val routeName = lastScreenRouteName ?: return
    if (scope.screen == routeName) {
      scope.screen = null
    }
    if (scope.contexts.app?.viewNames == listOf(routeName)) {
      scope.contexts.app?.viewNames = null
    }
    lastScreenRouteName = null
  }
}

private class NavTransactionManager(
  private val scopes: IScopes,
  private val navigationOp: String,
  private val transactionOrigin: String,
) {

  private var activeNavTransaction: ITransaction? = null

  /** Starts an idle navigation transaction, or no-ops if another transaction is already active. */
  fun start(
    routeName: String,
    arguments: Map<String, Any?>,
    snapshot: (ITransaction) -> Unit,
  ) {
    clearFinishedScopeTransaction()

    if (scopes.transaction != null) {
      scopes.options.logger.log(
        DEBUG,
        "Nav3 transaction for route %s won't be created because another transaction is active.",
        routeName,
      )

      return
    }

    val transactionOptions =
      TransactionOptions().also {
        it.isWaitForChildren = true
        it.idleTimeout = scopes.options.idleTimeout
        val deadlineTimeoutMillis = scopes.options.deadlineTimeout
        it.deadlineTimeout = if (deadlineTimeoutMillis <= 0) null else deadlineTimeoutMillis
        it.isTrimEnd = true
      }

    val transaction =
      scopes.startTransaction(
        TransactionContext(routeName, TransactionNameSource.ROUTE, navigationOp),
        transactionOptions,
      )

    activeNavTransaction = transaction

    transaction.apply {
      spanContext.origin = transactionOrigin
      if (arguments.isNotEmpty()) {
        setData("arguments", arguments)
      }
      snapshot(this)
    }

    scopes.configureScope { scope ->
      scope.withTransaction { tx ->
        if (tx == null) {
          scope.transaction = transaction
        }
      }
    }
  }

  /** Finishes and unsets the active navigation transaction, if one exists. */
  fun stop(scope: IScope) {
    val transaction = activeNavTransaction ?: return
    val status = transaction.status ?: SpanStatus.OK
    transaction.finish(status)

    scope.withTransaction { tx ->
      if (tx == transaction) {
        scope.clearTransaction()
      }
    }

    activeNavTransaction = null
  }

  /** Clears a stale finished transaction that's still bound to the default scope. */
  private fun clearFinishedScopeTransaction() {
    scopes.configureScope { scope ->
      scope.withTransaction { tx ->
        if (tx?.isFinished == true) {
          scope.clearTransaction()
        }
      }
    }
  }
}
