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
import java.lang.ref.WeakReference

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

  private var previousBackStackEntry: WeakReference<T>? = null
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
      scopes.configureScope { scope ->
        // Always update the recorded backstack, as any of its entries may have changed.
        scope.updateNavigationContext(backStack, options, updateWarningState)

        // Return early if there's nowhere to go or if the top of the back stack hasn't changed...
        val previousTop: T? = previousBackStackEntry?.get()
        val currentTop: T? = backStack.lastOrNull()

        if (currentTop == null) {
          handleEmptyBackStack(scope)
          return@configureScope
        }
        if (previousTop === currentTop) {
          return@configureScope
        }

        // ...otherwise record data for the new nav destination.
        handleNewTop(scope, previousTop, currentTop, backStack, updateWarningState)
      }
    }
  }

  internal fun cleanup() {
    previousBackStackEntry = null

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
    previousTop: T?,
    currentTop: T,
    backStack: List<T>,
    updateWarningState: RouteTranslator.UpdateWarningState,
  ) {
    val routeName = routeTranslator.resolveRouteName(currentTop)
    val arguments = routeTranslator.resolveArguments(currentTop, updateWarningState)

    if (scopes.options.isEnableScreenTracking) {
      screenTracker.track(scope, routeName)
    }

    if (options.enableNavigationBreadcrumbs) {
      scopes.addNav3Breadcrumb(previousTop, currentTop, routeName, arguments, updateWarningState)
    }

    navTransactions.stop(scope)

    if (areNavigationTransactionsEnabled) {
      navTransactions.start(routeName, arguments) { transaction ->
        transaction.snapshotTrackedNavigationContext(routeName, backStack, updateWarningState)
      }
    } else {
      scope.rotatePropagationContext()
    }

    previousBackStackEntry = WeakReference(currentTop)
  }

  private fun handleEmptyBackStack(scope: IScope) {
    navTransactions.stop(scope)
    screenTracker.clear(scope)
    previousBackStackEntry = null
  }

  private fun IScope.updateNavigationContext(
    backStack: List<T>,
    options: SentryNavOptions,
    updateWarningState: RouteTranslator.UpdateWarningState,
  ) {
    if (!options.captureBackStack) {
      this.removeContexts(NAVIGATION_CONTEXT_KEY)
      return
    }

    val entries = routeTranslator.toRouteEntries(backStack, updateWarningState)
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
    routeName: String,
    backStack: List<T>,
    updateWarningState: RouteTranslator.UpdateWarningState,
  ) {
    val appContext = contexts.app ?: App().also { contexts.setApp(it) }
    appContext.viewNames = listOf(routeName)

    if (options.captureBackStack) {
      val entries = routeTranslator.toRouteEntries(backStack, updateWarningState)
      if (entries.isNotEmpty()) {
        setContext(NAVIGATION_CONTEXT_KEY, mapOf("backstack" to entries))
      }
    }
  }

  private fun IScope.rotatePropagationContext() {
    withPropagationContext { setPropagationContext(PropagationContext()) }
  }

  private fun IScopes.addNav3Breadcrumb(
    fromEntry: T?,
    toEntry: T,
    routeName: String,
    arguments: Map<String, Any?>,
    updateWarningState: RouteTranslator.UpdateWarningState,
  ) {
    val breadcrumb =
      Breadcrumb().apply {
        type = NAVIGATION_OP
        category = NAVIGATION_OP

        fromEntry?.let { prev ->
          data["from"] = routeTranslator.resolveRouteName(prev)
          val fromArgs = routeTranslator.resolveArguments(prev, updateWarningState)
          if (fromArgs.isNotEmpty()) {
            data["from_arguments"] = fromArgs
          }
        }

        data["to"] = routeName
        if (arguments.isNotEmpty()) {
          data["to_arguments"] = arguments
        }

        level = INFO
      }

    val hint = Hint()
    hint.set(TypeCheckHint.NAV3_DESTINATION, toEntry)
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

  /** Starts an idle navigation transaction, or no-ops if another span context is already active. */
  fun start(
    routeName: String,
    arguments: Map<String, Any?>,
    snapshot: (ITransaction) -> Unit,
  ) {
    clearFinishedScopeTransaction()

    if (scopes.span != null) {
      scopes.options.logger.log(
        DEBUG,
        "Nav3 transaction for route %s won't be created because another transaction or span is active.",
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
