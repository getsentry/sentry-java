package io.sentry.compose.navigation3

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.PropagationContext
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.compose.navigation3.BackStackObserver.Companion.NAVIGATION_CONTEXT_KEY
import io.sentry.compose.navigation3.PreparedChange.BackStackHasNewTop
import io.sentry.compose.navigation3.PreparedChange.BackStackHasSameTop
import io.sentry.compose.navigation3.PreparedChange.BackStackIsEmpty
import io.sentry.compose.navigation3.RouteTranslator.RetentionPolicy
import io.sentry.protocol.App
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import java.lang.ref.WeakReference

/**
 * Observes the back stack managed by a single [SentryNavEffect] and records Sentry state as the
 * back stack is updated.
 *
 * **Top of the stack == the current screen**
 *
 * This class treats top of the back stack as the current navigation destination and visible screen.
 * It knows nothing about composite Scenes or multipane navigation scenarios.
 *
 * **Entry identity determines whether the top has changed**
 *
 * Referential equality (===), not structural equality, is used to determine whether the top of the
 * incoming back stack has changed. That approach:
 *
 * - matches the typical Nav3 SnapshotStateList, where an entry instance has a stable identity for
 *   its lifetime in the stack;
 * - mirrors [BackStackKey]'s policy;
 * - doesn't depend on host-provided `equals()` / `hashCode()`, which can be absent, incorrect, or
 *   expensive; and
 * - ensures we don't miss reporting a genuine top-of-stack change.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Clients should serialize calls to [onBackStackChanged] and
 * [cleanup] (e.g., via invocation from an `*Effect` or another form of thread confinement).
 */
@Suppress("TooManyFunctions")
internal class BackStackObserver<T : Any>(
  private val scopes: IScopes,
  private val options: SentryNavOptions,
  private val extractors: () -> RouteExtractors<T>,
) {

  private val navTransactions = NavTransactionManager(scopes, NAVIGATION_OP, TRANSACTION_ORIGIN)
  private val screenTracker = ScreenTracker()
  private val routeTranslator = RouteTranslator(extractors, scopes.options.logger)

  // Safe because the host back stack retains the current top entry strongly between updates.
  private var previousTopEntry: WeakReference<T>? = null
  private var previousTopRoute: Route? = null

  private val areNavigationTransactionsEnabled: Boolean
    get() = scopes.options.isTracingEnabled && options.enableNavigationTransactions

  init {
    addIntegrationToSdkVersion("ComposeNavigation3")
  }

  internal companion object {

    private const val BACKSTACK_KEY = "backstack"
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
   * Note: This method is ***not*** idempotent. Callers should protect against repeat invocations
   * with the same back stack to avoid emitting duplicate Sentry data.
   */
  internal fun onBackStackChanged(backStack: List<T>) {
    val change = prepareChange(backStack)
    scopes.configureScope { scope -> applyChange(scope, change) }
  }

  internal fun cleanup() {
    previousTopEntry = null
    previousTopRoute = null

    scopes.configureScope { scope ->
      navTransactions.stop(scope)
      screenTracker.clear(scope)

      if (options.captureBackStack) {
        // This observer owns the nav context while it's in the composition, and cleanup removes
        // it to avoid leaking stale back stack data after observation stops. If the host app
        // replaces one observer with another, there may be a brief gap where events lack nav
        // context. Apps should keep the observer at the nav root so cleanup only runs when the
        // navigation session is ending, not during normal destination changes.
        scope.removeNavigationContext()
      }
    }
  }

  private fun prepareChange(backStack: List<T>): PreparedChange<T> {
    val topEntry = backStack.lastOrNull() ?: return BackStackIsEmpty
    val data = backStack.extractData()

    return if (topEntry === previousTopEntry?.get()) {
      BackStackHasSameTop(data)
    } else {
      BackStackHasNewTop(previousTopRoute, data)
    }
  }

  private fun applyChange(scope: IScope, change: PreparedChange<T>) {
    when (change) {
      is BackStackIsEmpty -> handleEmptyBackStack(scope)

      is BackStackHasNewTop -> {
        handleNewTop(scope, change.previousTopRoute, change.data)
        storeAsPreviousTop(change.data.topEntry, change.data.topRoute)
      }

      is BackStackHasSameTop -> {
        handleSameTop(scope, change.data)
        storeAsPreviousTop(change.data.topEntry, change.data.topRoute)
      }
    }
  }

  /**
   * Extracts Sentry data from the receiver (i.e., a list of host app back stack entries) in the
   * form of a [BackStackData].
   *
   * Throws if the receiver is empty.
   */
  private fun List<T>.extractData(): BackStackData<T> {
    check(this.isNotEmpty())

    val topEntry = this.last()
    val shouldCaptureBackStack = options.captureBackStack && options.maxCapturedBackStackEntries > 0

    val entriesToTranslate =
      when {
        shouldCaptureBackStack ->
          // Reverse entries so they're displayed with the newest entry on top in the Sentry UI.
          this.takeLast(options.maxCapturedBackStackEntries).asReversed()

        // We always need to translate the top entry for use with breadcrumbs, etc., even if we're
        // not capturing the back stack.
        else -> listOf(topEntry)
      }

    val routes =
      routeTranslator.translate(
        backStackEntries = entriesToTranslate,
        retentionPolicy = RetentionPolicy.KEEP_FIRST,
      )

    return BackStackData(
      topEntry = topEntry,
      topRoute = routes.first(),
      capturedRoutes = if (shouldCaptureBackStack) routes else emptyList(),
    )
  }

  private fun handleNewTop(
    scope: IScope,
    previousTop: Route?,
    currentBackStack: BackStackData<T>,
  ) {
    val currentTopRoute = currentBackStack.topRoute

    scope.updateNavigationContext(currentBackStack.capturedRoutes)

    if (scopes.options.isEnableScreenTracking) {
      screenTracker.track(scope, currentTopRoute.name)
    }

    if (options.enableNavigationBreadcrumbs) {
      scopes.addNav3Breadcrumb(
        from = previousTop,
        toEntry = currentBackStack.topEntry,
        toRoute = currentBackStack.topRoute,
      )
    }

    navTransactions.stop(scope)

    if (areNavigationTransactionsEnabled) {
      navTransactions
        .start(
          scope,
          currentTopRoute.name,
          currentTopRoute.arguments,
        )
        ?.updateNavigationContext(scope, currentBackStack)
    } else {
      // Rotate the propagation context.
      scope.withPropagationContext { scope.setPropagationContext(PropagationContext()) }
    }
  }

  private fun handleSameTop(scope: IScope, backStack: BackStackData<T>) {
    scope.updateNavigationContext(backStack.capturedRoutes)
  }

  private fun handleEmptyBackStack(scope: IScope) {
    scope.updateNavigationContext(emptyList())
    navTransactions.stop(scope)
    screenTracker.clear(scope)
    previousTopEntry = null
    previousTopRoute = null
  }

  private fun storeAsPreviousTop(topEntry: T, topRoute: Route) {
    previousTopEntry = WeakReference(topEntry)
    previousTopRoute = topRoute
  }

  private fun IScope.updateNavigationContext(capturedRoutes: List<Route>) {
    if (capturedRoutes.isEmpty()) {
      this.removeNavigationContext()
    } else {
      this.setContexts(NAVIGATION_CONTEXT_KEY, capturedRoutes.toNavigationContext())
    }
  }

  private fun IScope.removeNavigationContext() {
    // We purposefully don't call IScope.removeContexts(), as it doesn't notify IScopeObserver and
    // therefore doesn't write its updates to disk ¯\_ (ツ)_/¯.
    this.setContexts(NAVIGATION_CONTEXT_KEY, null as Any?)
  }

  /**
   * Updates the receiver's context with the provided navigation info.
   *
   * Needed because transactions inherit base scope context on a per-key basis unless transactions
   * have their own values for those keys. In our case, we need to keep fresh back stack and route
   * values in the base context for purposes of crash reporting. But those values will often advance
   * past what's relevant to a given transaction. This method prevents misassociation by binding
   * proper values to the transaction context instead.
   */
  private fun ITransaction.updateNavigationContext(scope: IScope, backStack: BackStackData<T>) {
    if (scopes.options.isEnableScreenTracking) {
      val appContext = contexts.app ?: io.sentry.protocol.Contexts(scope.contexts).app ?: App()

      appContext.viewNames = listOf(backStack.topRoute.name)
      contexts.setApp(appContext)
    }

    if (options.captureBackStack && backStack.capturedRoutes.isNotEmpty()) {
      setContext(NAVIGATION_CONTEXT_KEY, backStack.capturedRoutes.toNavigationContext())
    }
  }

  /** Builds the `{"backstack": [...]}` map bound under [NAVIGATION_CONTEXT_KEY]. */
  private fun List<Route>.toNavigationContext(): Map<String, Any?> =
    mapOf(BACKSTACK_KEY to serialize())

  private fun IScopes.addNav3Breadcrumb(
    from: Route?,
    toEntry: T,
    toRoute: Route,
  ) {
    val breadcrumb =
      Breadcrumb().apply {
        type = NAVIGATION_OP
        category = NAVIGATION_OP

        from?.let {
          data["from"] = it.name
          if (it.arguments.isNotEmpty()) {
            data["from_arguments"] = it.arguments
          }
        }

        data["to"] = toRoute.name
        if (toRoute.arguments.isNotEmpty()) {
          data["to_arguments"] = toRoute.arguments
        }

        level = INFO
      }

    val hint = Hint()
    hint.set(TypeCheckHint.ANDROID_NAV3_DESTINATION, toEntry)
    this.addBreadcrumb(breadcrumb, hint)
  }
}

/**
 * A model for applying one back stack update.
 *
 * Lets us separate change preparation from its application so that the [IScopes.configureScope]
 * callback in charge of application can use already-computed navigation state. Otherwise, any
 * exceptions thrown during state computation would be swallowed by `configureScope`'s over-broad
 * `catch` clause.
 */
private sealed interface PreparedChange<out T : Any> {

  /** The incoming back stack is empty. */
  data object BackStackIsEmpty : PreparedChange<Nothing>

  /**
   * The top of the back stack has changed, and one or more entries below it may have been updated.
   */
  data class BackStackHasNewTop<T : Any>(
    val previousTopRoute: Route?,
    val data: BackStackData<T>,
  ) : PreparedChange<T>

  /** The top of the back stack is unchanged, but one or more entries below it have been updated. */
  data class BackStackHasSameTop<T : Any>(val data: BackStackData<T>) : PreparedChange<T>
}

/** Info extracted from the host app's back stack in a form suitable for Sentry data. */
private data class BackStackData<T>(
  val topEntry: T,
  val topRoute: Route,
  /**
   * [Route]s representing the newest [SentryNavOption.maxCapturedBackStackEntries] entries from the
   * host app's back stack. Possibly empty.
   */
  val capturedRoutes: List<Route>,
)

/** Tracks a provided name as the current visible screen. */
private class ScreenTracker {

  private var lastScreenName: String? = null

  fun track(scope: IScope, screenName: String) {
    scope.screen = screenName
    lastScreenName = screenName
  }

  fun clear(scope: IScope) {
    val routeName = lastScreenName ?: return
    if (scope.screen == routeName) {
      scope.screen = null
    }
    lastScreenName = null
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
    scope: IScope,
    routeName: String,
    arguments: Map<String, Any?>,
  ): ITransaction? {
    clearFinishedScopeTransaction(scope)

    if (scope.transaction != null) {
      scopes.options.logger.log(
        DEBUG,
        "Nav3 transaction for route %s won't be created because another transaction is active.",
        routeName,
      )

      return null
    }

    val transactionOptions =
      TransactionOptions().also {
        it.isWaitForChildren = true
        it.idleTimeout = scopes.options.idleTimeout
        val deadlineTimeoutMillis = scopes.options.deadlineTimeout
        it.deadlineTimeout = if (deadlineTimeoutMillis <= 0) null else deadlineTimeoutMillis
        it.isTrimEnd = true
        it.origin = transactionOrigin
      }

    val transaction =
      scopes.startTransaction(
        TransactionContext(routeName, TransactionNameSource.ROUTE, navigationOp),
        transactionOptions,
      )

    activeNavTransaction = transaction

    transaction.apply {
      if (arguments.isNotEmpty()) {
        setData("arguments", arguments)
      }
    }

    scope.withTransaction { tx ->
      if (tx == null) {
        scope.transaction = transaction
      }
    }

    return transaction
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
  private fun clearFinishedScopeTransaction(scope: IScope) {
    scope.withTransaction { tx ->
      if (tx?.isFinished == true) {
        scope.clearTransaction()
      }
    }
  }
}
