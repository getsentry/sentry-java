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
import io.sentry.compose.navigation3.PreparedChange.BackStackHasNewTop
import io.sentry.compose.navigation3.PreparedChange.BackStackHasSameTop
import io.sentry.compose.navigation3.PreparedChange.BackStackIsEmpty
import io.sentry.compose.navigation3.RouteTranslator.RetentionPolicy
import io.sentry.protocol.App
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import java.lang.ref.WeakReference

private const val BACKSTACK_KEY = "backstack"
private const val NAVIGATION_CONTEXT_KEY = "navigation"
private const val NAVIGATION_OP: String = "navigation"
private const val TRANSACTION_ORIGIN = "auto.navigation.nav3"

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
internal class BackStackObserver<T : Any>(
  private val scopes: IScopes,
  private val options: SentryNavOptions,
  extractors: () -> RouteExtractors<T>,
) {

  private val routeTranslator = RouteTranslator(extractors, scopes.options.logger)

  private val navTransaction = NavTransaction(scopes, NAVIGATION_OP, TRANSACTION_ORIGIN)
  private val navContext = NavContext(scopes, options)
  private val navScreen = NavScreen()
  private val navBreadcrumbs = NavBreadcrumbs(scopes)

  // Safe because the host back stack retains the current top entry strongly between updates.
  private var previousTopEntry: WeakReference<T>? = null
  private var previousTopRoute: Route? = null

  private val areNavigationTransactionsEnabled: Boolean
    get() = scopes.options.isTracingEnabled && options.enableNavigationTransactions

  init {
    addIntegrationToSdkVersion("ComposeNavigation3")
  }

  internal companion object {
    init {
      SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-android-navigation3", BuildConfig.VERSION_NAME)
    }
  }

  /**
   * Updates Sentry nav data based on the provided [backStack].
   *
   * **Data generated**
   *
   * By default, the following happens every time the top of the back stack changes:
   *
   * - a breadcrumb is emitted
   * - a screen name is recorded
   * - a new nav transaction is started and the old nav transaction, if any, is stopped.
   *
   * Names and other info for all of the above are derived from the new back stack top.
   *
   * By default, a record of the current back stack is recorded for every call, irrespective of
   * whether the top changes.
   *
   * Defaults can be configured via the [SentryNavOptions] instance passed to this class's
   * constructor. (Screen names can be disabled via [SentryOptions.setEnableScreenTracking].)
   *
   * **Not idempotent**
   *
   * This method is ***not*** idempotent. Callers should protect against repeat invocations with the
   * same back stack to avoid emitting duplicate Sentry data.
   */
  internal fun onBackStackChanged(backStack: List<T>) {
    val change = prepareChange(backStack)
    scopes.configureScope { scope -> applyChange(scope, change) }
  }

  internal fun cleanup() {
    previousTopEntry = null
    previousTopRoute = null

    scopes.configureScope { scope ->
      navTransaction.stop(scope)
      navScreen.clear(scope)

      if (options.captureBackStack) {
        // This observer owns the nav context while it's in the composition, and cleanup removes
        // it to avoid leaking stale back stack data after observation stops. If the host app
        // replaces one observer with another, there may be a brief gap where events lack nav
        // context. Apps should keep the observer at the nav root so cleanup only runs when the
        // navigation session is ending, not during normal destination changes.
        navContext.update(scope, emptyList())
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
        handleNewTop(scope, change.previousTop, change.backStack)
        storeAsPreviousTop(change.backStack.topEntry, change.backStack.topRoute)
      }

      is BackStackHasSameTop -> {
        handleSameTop(scope, change.backStack)
        storeAsPreviousTop(change.backStack.topEntry, change.backStack.topRoute)
      }
    }
  }

  /**
   * Extracts Sentry-compatible data from the receiver (i.e., a list of host app back stack entries)
   * in the form of a [BackStackData].
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

    navContext.update(scope, currentBackStack.capturedRoutes)

    if (scopes.options.isEnableScreenTracking) {
      navScreen.update(scope, currentTopRoute)
    }

    if (options.enableNavigationBreadcrumbs) {
      navBreadcrumbs.emit(
        from = previousTop,
        toEntry = currentBackStack.topEntry,
        toRoute = currentBackStack.topRoute,
      )
    }

    navTransaction.stop(scope)

    if (areNavigationTransactionsEnabled) {
      navTransaction
        .start(
          scope,
          currentTopRoute.name,
          currentTopRoute.arguments,
        )
        ?.let { transaction -> navContext.updateTransaction(transaction, scope, currentBackStack) }
    } else {
      // Rotate the propagation context.
      scope.withPropagationContext { scope.setPropagationContext(PropagationContext()) }
    }
  }

  private fun handleSameTop(scope: IScope, backStack: BackStackData<T>) {
    navContext.update(scope, backStack.capturedRoutes)
  }

  private fun handleEmptyBackStack(scope: IScope) {
    navTransaction.stop(scope)
    navScreen.clear(scope)
    navContext.update(scope, emptyList())
    previousTopEntry = null
    previousTopRoute = null
  }

  private fun storeAsPreviousTop(topEntry: T, topRoute: Route) {
    previousTopEntry = WeakReference(topEntry)
    previousTopRoute = topRoute
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
    val previousTop: Route?,
    val backStack: BackStackData<T>,
  ) : PreparedChange<T>

  /** The top of the back stack is unchanged, but one or more entries below it have been updated. */
  data class BackStackHasSameTop<T : Any>(val backStack: BackStackData<T>) : PreparedChange<T>
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

/** A helper class for managing nav transactions. */
private class NavTransaction(
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

/** A helper class for updating [nav context][NAVIGATION_CONTEXT_KEY]. */
private class NavContext(private val scopes: IScopes, private val options: SentryNavOptions) {

  fun update(scope: IScope, backStackRoutes: List<Route>) {
    if (backStackRoutes.isEmpty()) {
      removeNavContext(scope)
    } else {
      scope.setContexts(NAVIGATION_CONTEXT_KEY, backStackRoutes.toNavigationContext())
    }
  }

  /**
   * Updates the transaction with the provided navigation info.
   *
   * Needed because transactions inherit base scope context on a per-key basis unless transactions
   * have their own values for those keys. In our case, we need to keep fresh back stack and route
   * values in the base context for purposes of crash reporting. But those values will often advance
   * past what's relevant to a given transaction. This method prevents misassociation by binding
   * proper values to the transaction context instead.
   */
  fun <T : Any> updateTransaction(
    transaction: ITransaction,
    scope: IScope,
    backStack: BackStackData<T>,
  ) {
    if (scopes.options.isEnableScreenTracking) {
      val appContext =
        transaction.contexts.app ?: io.sentry.protocol.Contexts(scope.contexts).app ?: App()

      appContext.viewNames = listOf(backStack.topRoute.name)
      transaction.contexts.setApp(appContext)
    }

    if (options.captureBackStack && backStack.capturedRoutes.isNotEmpty()) {
      transaction.setContext(NAVIGATION_CONTEXT_KEY, backStack.capturedRoutes.toNavigationContext())
    }
  }

  private fun removeNavContext(scope: IScope) {
    // We purposefully don't call IScope.removeContexts(), as it doesn't notify IScopeObserver and
    // therefore doesn't write its updates to disk ¯\_ (ツ)_/¯.
    scope.setContexts(NAVIGATION_CONTEXT_KEY, null as Any?)
  }

  /** Builds the `{"backstack": [...]}` map bound under [NAVIGATION_CONTEXT_KEY]. */
  private fun List<Route>.toNavigationContext(): Map<String, Any?> =
    mapOf(BACKSTACK_KEY to serialize())
}

/** A helper class for updating the tracked screen name. */
private class NavScreen {

  private var lastScreenName: String? = null

  fun update(scope: IScope, currentRoute: Route) {
    scope.screen = currentRoute.name
    lastScreenName = currentRoute.name
  }

  fun clear(scope: IScope) {
    val routeName = lastScreenName ?: return
    if (scope.screen == routeName) {
      scope.screen = null
    }
    lastScreenName = null
  }
}

/** A helper class for generating nav breadcrumbs. */
private class NavBreadcrumbs(private val scopes: IScopes) {

  fun <T : Any> emit(
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
    scopes.addBreadcrumb(breadcrumb, hint)
  }
}
