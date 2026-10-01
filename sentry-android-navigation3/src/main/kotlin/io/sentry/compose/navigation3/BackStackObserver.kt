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
import io.sentry.compose.navigation3.BackStackConverter.RetentionPolicy
import io.sentry.compose.navigation3.PreparedChange.BackStackHasNewTop
import io.sentry.compose.navigation3.PreparedChange.BackStackHasSameTop
import io.sentry.compose.navigation3.PreparedChange.BackStackIsEmpty
import io.sentry.protocol.App
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import java.lang.ref.WeakReference

private const val NAVIGATION_OP: String = "navigation"

/**
 * Observes the back stack managed by a single [SentryNavEffect] and records Sentry state as the
 * back stack is updated.
 *
 * **Top of the stack == the current screen**
 *
 * This class treats top of the back stack as the current navigation destination and visible screen.
 * It knows nothing about composite Scenes or multi-pane destinations.
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
  entryMapper: ForwardingBackStackEntryMapper<T>,
) {

  private val backStackConverter = BackStackConverter(entryMapper, scopes.options.logger)

  private val navTransaction = NavTransaction(scopes)
  private val navBreadcrumbs = NavBreadcrumbs(scopes)
  private val navScreen = NavScreen()
  private val navContext = NavContext(scopes, options)

  // Safe because the host back stack retains the current top entry strongly between updates.
  private var previousTop: WeakReference<T>? = null
  private var previousTopNormalized: NormalizedSentryBackStackEntry? = null

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
   * - a new nav transaction is started and the old nav transaction, if any, is stopped
   * - a breadcrumb is emitted
   * - a screen name is recorded
   *
   * Names and other info for all of the above are derived from the new back stack top.
   *
   * By default, a record of the current back stack is recorded every time this method is invoked,
   * irrespective of whether the top of the back stack changes, as the host app may have modified
   * non-top entries.
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
    previousTop = null
    previousTopNormalized = null

    scopes.configureScope { scope ->
      navTransaction.stop(scope)
      navScreen.clear(scope)

      if (options.captureBackStack) {
        // This observer owns the nav context while it's in the composition, and cleanup removes
        // it to avoid leaking stale back stack data after observation stops. If the host app
        // replaces one observer with another, there may be a brief gap where events lack nav
        // context. Apps should keep the observer at the nav root so cleanup only runs when the
        // navigation session is ending, not during normal destination changes.
        navContext.clear(scope)
      }
    }
  }

  private fun prepareChange(backStack: List<T>): PreparedChange<T> {
    val topEntry = backStack.lastOrNull() ?: return BackStackIsEmpty
    val data = backStack.extractData()

    return if (topEntry === previousTop?.get()) {
      BackStackHasSameTop(data)
    } else {
      BackStackHasNewTop(previousTopNormalized, data)
    }
  }

  private fun applyChange(scope: IScope, change: PreparedChange<T>) {
    when (change) {
      is BackStackIsEmpty -> handleEmptyBackStack(scope)
      is BackStackHasNewTop -> handleNewTop(scope, change.previousTop, change.backStack)
      is BackStackHasSameTop -> handleSameTop(scope, change.backStack)
    }
  }

  /**
   * Extracts Sentry-compatible data from the receiver (i.e., the host app back stack) in the form
   * of a [BackStackData] instance.
   *
   * Throws if the receiver is empty.
   */
  private fun List<T>.extractData(): BackStackData<T> {
    check(this.isNotEmpty())

    val topEntry = this.last()
    val shouldCaptureBackStack = options.captureBackStack && options.maxCapturedBackStackEntries > 0

    val entriesToConvert =
      when {
        shouldCaptureBackStack ->
          // Reverse entries so they're displayed with the newest entry on top in the Sentry UI.
          this.takeLast(options.maxCapturedBackStackEntries).asReversed()

        // We always need to convert the top entry for use with breadcrumbs, etc., even if we're
        // not capturing the back stack.
        else -> listOf(topEntry)
      }

    val normalizedEntries =
      backStackConverter.convert(
        backStack = entriesToConvert,
        retentionPolicy = RetentionPolicy.KEEP_FIRST,
      )

    return BackStackData(
      topEntry = topEntry,
      topEntryNormalized = normalizedEntries.first(),
      capturedEntriesNormalized = if (shouldCaptureBackStack) normalizedEntries else emptyList(),
    )
  }

  private fun handleNewTop(
    scope: IScope,
    previousTop: NormalizedSentryBackStackEntry?,
    currentBackStack: BackStackData<T>,
  ) {
    val currentTop = currentBackStack.topEntryNormalized

    navContext.update(scope, currentBackStack.capturedEntriesNormalized)

    if (scopes.options.isEnableScreenTracking) {
      navScreen.update(scope, currentTop)
    }

    if (options.enableNavigationBreadcrumbs) {
      navBreadcrumbs.emit(
        fromEntry = previousTop,
        toEntry = currentBackStack.topEntryNormalized,
        toRawEntry = currentBackStack.topEntry,
      )
    }

    navTransaction.stop(scope)

    if (areNavigationTransactionsEnabled) {
      navTransaction
        .start(
          scope,
          currentTop.name,
          currentTop.arguments,
        )
        ?.let { transaction -> navContext.updateTransaction(transaction, scope, currentBackStack) }
    } else {
      // Rotate the propagation context.
      scope.withPropagationContext { scope.setPropagationContext(PropagationContext()) }
    }

    storeAsPreviousTop(currentBackStack.topEntry, currentBackStack.topEntryNormalized)
  }

  private fun handleSameTop(scope: IScope, backStack: BackStackData<T>) {
    navContext.update(scope, backStack.capturedEntriesNormalized)
    storeAsPreviousTop(backStack.topEntry, backStack.topEntryNormalized)
  }

  private fun handleEmptyBackStack(scope: IScope) {
    navTransaction.stop(scope)
    navContext.clear(scope)
    navScreen.clear(scope)
    clearPreviousTop()
  }

  private fun storeAsPreviousTop(topEntry: T, topEntryNormalized: NormalizedSentryBackStackEntry) {
    previousTop = WeakReference(topEntry)
    previousTopNormalized = topEntryNormalized
  }

  private fun clearPreviousTop() {
    previousTop = null
    previousTopNormalized = null
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
    val previousTop: NormalizedSentryBackStackEntry?,
    val backStack: BackStackData<T>,
  ) : PreparedChange<T>

  /** The top of the back stack is unchanged, but one or more entries below it have been updated. */
  data class BackStackHasSameTop<T : Any>(val backStack: BackStackData<T>) : PreparedChange<T>
}

/** Info extracted from the host app's back stack in a form suitable for Sentry data. */
private data class BackStackData<T>(
  val topEntry: T,
  val topEntryNormalized: NormalizedSentryBackStackEntry,
  /**
   * [NormalizedSentryBackStackEntry]s representing the newest
   * [SentryNavOption.maxCapturedBackStackEntries] entries from the host app's back stack.
   *
   * Possibly empty.
   */
  val capturedEntriesNormalized: List<NormalizedSentryBackStackEntry>,
)

/** A helper class for managing nav transactions. */
private class NavTransaction(private val scopes: IScopes) {

  private companion object {
    private const val TRANSACTION_ORIGIN = "auto.navigation.nav3"
  }

  private var activeNavTransaction: ITransaction? = null

  /** Starts an idle navigation transaction, or no-ops if another transaction is already active. */
  fun start(
    scope: IScope,
    name: String,
    arguments: Map<String, Any?>,
  ): ITransaction? {
    clearTransactionIfFinished(scope)

    if (scope.transaction != null) {
      scopes.options.logger.log(
        DEBUG,
        "Nav3 transaction for route %s won't be created because another transaction is active.",
        name,
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
        it.origin = TRANSACTION_ORIGIN
      }

    val transaction =
      scopes.startTransaction(
        TransactionContext(name, TransactionNameSource.ROUTE, NAVIGATION_OP),
        transactionOptions,
      )

    if (transaction.isNoOp) {
      return null
    }

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
  private fun clearTransactionIfFinished(scope: IScope) {
    scope.withTransaction { tx ->
      if (tx?.isFinished == true) {
        scope.clearTransaction()
      }
    }
  }
}

/** A helper class for updating [nav context][NAVIGATION_CONTEXT_KEY]. */
private class NavContext(private val scopes: IScopes, private val options: SentryNavOptions) {

  private companion object {
    private const val BACKSTACK_KEY = "backstack"
    private const val NAVIGATION_CONTEXT_KEY = "navigation"
  }

  fun update(scope: IScope, backStackEntries: List<NormalizedSentryBackStackEntry>) {
    if (backStackEntries.isEmpty()) {
      clear(scope)
      return
    }

    scope.setContexts(NAVIGATION_CONTEXT_KEY, backStackEntries.toBackStackMap())
  }

  fun clear(scope: IScope) {
    // We purposefully don't call IScope.removeContexts(), as it doesn't notify IScopeObserver and
    // therefore doesn't write its updates to disk ¯\_ (ツ)_/¯.
    scope.setContexts(NAVIGATION_CONTEXT_KEY, null as Any?)
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

      appContext.viewNames = listOf(backStack.topEntryNormalized.name)
      transaction.contexts.setApp(appContext)
    }

    if (options.captureBackStack && backStack.capturedEntriesNormalized.isNotEmpty()) {
      transaction.setContext(
        NAVIGATION_CONTEXT_KEY,
        backStack.capturedEntriesNormalized.toBackStackMap(),
      )
    }
  }

  /** Builds the `{"backstack": [...]}` map bound under [NAVIGATION_CONTEXT_KEY]. */
  private fun List<NormalizedSentryBackStackEntry>.toBackStackMap(): Map<String, Any?> =
    mapOf(BACKSTACK_KEY to serialize())
}

/** A helper class for updating the tracked screen name. */
private class NavScreen {

  private var lastScreenName: String? = null

  fun update(scope: IScope, currentEntry: NormalizedSentryBackStackEntry) {
    scope.screen = currentEntry.name
    lastScreenName = currentEntry.name
  }

  fun clear(scope: IScope) {
    val screenName = lastScreenName ?: return
    if (scope.screen == screenName) {
      scope.screen = null
    }
    lastScreenName = null
  }
}

/** A helper class for generating nav breadcrumbs. */
private class NavBreadcrumbs(private val scopes: IScopes) {

  fun <T : Any> emit(
    fromEntry: NormalizedSentryBackStackEntry?,
    toEntry: NormalizedSentryBackStackEntry,
    toRawEntry: T,
  ) {
    val breadcrumb =
      Breadcrumb().apply {
        type = NAVIGATION_OP
        category = NAVIGATION_OP

        fromEntry?.let {
          data["from"] = it.name
          if (it.arguments.isNotEmpty()) {
            data["from_arguments"] = it.arguments
          }
        }

        data["to"] = toEntry.name
        if (toEntry.arguments.isNotEmpty()) {
          data["to_arguments"] = toEntry.arguments
        }

        level = INFO
      }

    val hint = Hint()
    hint.set(TypeCheckHint.ANDROID_NAV3_DESTINATION, toRawEntry)
    scopes.addBreadcrumb(breadcrumb, hint)
  }
}
