package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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
import io.sentry.compose.navigation3.BackStackConverter.RetentionPolicy
import io.sentry.compose.navigation3.PreparedChange.BackStackHasNewTop
import io.sentry.compose.navigation3.PreparedChange.BackStackHasSameTop
import io.sentry.compose.navigation3.PreparedChange.BackStackIsEmpty
import io.sentry.protocol.App
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal const val NAVIGATION_CONTEXT_KEY: String = "Navigation"

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
 * **Coexisting with other observers**
 *
 * All observer instances mutate a single [IScope] by default. In general, more than one
 * simultaneously active observer writing to the same scope is ***not*** supported and can result in
 * clobbered or interleaved updates, as well as undefined transaction behavior.
 *
 * This class does, however, coordinate updates from multiple observers when the lifecycles of the
 * Activity or Fragment each observer instance lives in briefly overlap. Doing so lets us support
 * common app architectures where i) each Activity or Fragment owns its own `NavDisplay` <>
 * `SentryNavEffect` pair, and ii) no `NavDisplay` <> `SentryNavEffect` pair is meant to be active
 * at the same time as another (e.g., no nesting or
 * [multi-resume mode](https://source.android.com/docs/core/display/multi_display/multi-resume)).
 *
 * // TODO ADAM: Update as needed. The basic policy is that:
 *
 * 1. the active observer owns the shared nav state;
 * 2. obscured observers retain changes without publishing them; and
 * 3. when ownership returns to an observer, it applies any retained changes and restores its state.
 *
 * The following diagram illustrates how observers coordinate their updates during lifecycle owner
 * overlaps ("nav data" refers to breadcrumbs, screen names, and back stack context):
 * ```
 *  Time
 *    │
 *    ▼
 *
 *  Observer A                    Shared scope                    Observer B
 *  ──────────                    ────────────                    ──────────
 *      │
 *  First new top
 *      │
 *      ├── publish ────────────► Nav data: A
 *      └── bind ───────────────► Nav transaction: A
 *      │                                                             │
 *      │                                                       First new top
 *      │                                                             │
 *      │                         A's nav transaction ◄── unbind ─────┤
 *      │                         Nav data: B         ◄── publish ────┤
 *      │                         Nav transaction: B  ◄── bind ───────┤
 *      │                                                             │
 *  Back stack changes                                                │
 *      │                                                             │
 *      └── held while obscured                                       │
 *      │                                                             │
 *      │                                                         cleanup
 *      │                                                             │
 *      │                         B's data cleared ◄──────────────────┤
 *      │                         A becomes active ◄── coordinator ───┤
 *      │                                                             │
 *      ├── apply held changes ─► Nav data: A                         │
 *      └── reclaim ────────────► A's unfinished nav transaction      │
 *      │                                                             ▼
 *
 *  Alternative: A cleans up while B is active
 *      │
 *      └── clear A's own data only; B's published data remains
 *  ```
 *
 * **Lifecycle**
 *
 * Register this observer with the [Lifecycle] of the owner hosting its `NavDisplay`. Each time that
 * owner resumes, the observer [reclaims][onResume] the shared nav state. Registration is left to
 * the caller so it can be tied to an `*Effect` (and so never happens during composition), and so a
 * change of owner only requires re-registering rather than a new observer.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Clients should serialize calls to [onBackStackChanged],
 * [onStateChanged], and [cleanup] (e.g., via invocation from an `*Effect` or another form of thread
 * confinement).
 */
@Suppress("TooManyFunctions")
internal class BackStackObserver<T : Any>(
  private val scopes: IScopes,
  private val options: SentryNavOptions,
  entryMapper: ForwardingBackStackEntryMapper<T>,
) : LifecycleEventObserver {

  /** Shared with every other observer writing to the same [scopes]. */
  private val coordinator = NavScopesCoordinator.create(scopes)

  private val backStackConverter = BackStackConverter(entryMapper, scopes.options.logger)

  private val navTransaction = NavTransaction(scopes)
  private val navBreadcrumbs = NavBreadcrumbs(scopes)
  private val navScreen = NavScreen()
  private val navContext = NavContext(scopes, options)

  // Safe because the host back stack retains the current top entry strongly between updates.
  private var previousTop: WeakReference<T>? = null
  private var previousTopNormalized: NormalizedSentryBackStackEntry? = null

  /** Whether a new top was handled yet. Only the first may evict another observer's transaction. */
  private var hasHandledNewTop = false

  private var pendingChange: PreparedChange<T>? = null

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

    if (change is BackStackHasNewTop && !hasHandledNewTop) {
      coordinator.claimScope(this)
    }

    if (!coordinator.ownsScope(this)) {
      pendingChange = change
      return
    }

    scopes.configureScope { scope -> applyChange(scope, change) }
  }

  /**
   * Republishes this observer's nav state to [IScopes] whenever its lifecycle owner resumes (e.g.,
   * returning to an Activity), as another observer may have overwritten that state in the meantime.
   *
   * For an owner that's already resumed, [Lifecycle.addObserver] replays
   * [Lifecycle.Event.ON_RESUME] immediately, typically before the first [onBackStackChanged]; at
   * that point this observer has nothing to republish yet.
   *
   * No-ops for data this observer never published (e.g., screens when screen tracking is disabled),
   * so host-owned values are left alone.
   */
  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    if (event == Lifecycle.Event.ON_RESUME) {
      coordinator.claimScope(this)
      publishCurrentState()
    }
  }

  /**
   * Republishes this observer's state after the [coordinator] hands it the shared nav state, i.e.,
   * when the previously active observer [releases the scope][NavScopesCoordinator.releaseScope]
   * without this observer's lifecycle owner resuming.
   */
  internal fun onHandoff() {
    publishCurrentState()
  }

  private fun publishCurrentState() {
    scopes.configureScope { scope ->
      pendingChange?.let { change ->
        applyChange(scope, change)
        pendingChange = null
      }
      navScreen.republish(scope)
      navContext.republish(scope)
      navTransaction.reclaim(scope)
    }
  }

  internal fun cleanup() {
    previousTop = null
    previousTopNormalized = null
    pendingChange = null

    scopes.configureScope { scope ->
      navTransaction.stop(scope)
      // Only clear values this observer published; another observer may have replaced them.
      navScreen.clear(scope)
      navContext.clearIfPublished(scope)
    }
    coordinator.releaseScope(this)
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
      navBreadcrumbs.emit(fromEntry = previousTop, toEntry = currentBackStack.topEntryNormalized)
    }

    navTransaction.stop(scope)

    val isFirstNewTop = !hasHandledNewTop
    hasHandledNewTop = true

    if (areNavigationTransactionsEnabled) {
      navTransaction
        .start(
          scope,
          currentTop.name,
          currentTop.argumentsWithMetadata(),
          evictOtherNavTransaction = isFirstNewTop,
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
    navContext.clearIfPublished(scope)
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
   * [SentryNavOptions.maxCapturedBackStackEntries] entries from the host app's back stack.
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

  /**
   * Starts an idle navigation transaction, or no-ops if another transaction is already active.
   *
   * When [evictOtherNavTransaction] is true, a bound nav transaction owned by another observer is
   * unbound (not finished; its owner still finishes it) before the check. Non-nav transactions are
   * never evicted.
   */
  fun start(
    scope: IScope,
    name: String,
    arguments: Map<String, Any?>,
    evictOtherNavTransaction: Boolean = false,
  ): ITransaction? {
    clearTransactionIfFinished(scope)

    if (evictOtherNavTransaction) {
      unbindOtherNavTransaction(scope)
    }

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

  /**
   * Takes the scope back for this observer when its owner resumes: unbinds another observer's nav
   * transaction (never a non-nav transaction) and rebinds this observer's unfinished one if the
   * scope is then free.
   */
  fun reclaim(scope: IScope) {
    clearTransactionIfFinished(scope)
    unbindOtherNavTransaction(scope)

    val transaction = activeNavTransaction ?: return
    if (transaction.isFinished) {
      return
    }
    scope.withTransaction { tx ->
      if (tx == null) {
        scope.transaction = transaction
      }
    }
  }

  private fun unbindOtherNavTransaction(scope: IScope) {
    scope.withTransaction { tx ->
      if (
        tx != null && tx !== activeNavTransaction && tx.spanContext.origin == TRANSACTION_ORIGIN
      ) {
        scope.clearTransaction()
      }
    }
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
    private const val BACKSTACK_KEY = "Back Stack"
  }

  /** The exact nav context instance this helper last published, if any. */
  private var lastPublished: Map<String, Any?>? = null

  fun update(scope: IScope, backStackEntries: List<NormalizedSentryBackStackEntry>) {
    if (backStackEntries.isEmpty()) {
      clear(scope)
      return
    }

    val navigationContext = backStackEntries.toBackStackMap()
    scope.setContexts(NAVIGATION_CONTEXT_KEY, navigationContext)
    lastPublished = navigationContext
  }

  fun clear(scope: IScope) {
    // We purposefully don't call IScope.removeContexts(), as it doesn't notify IScopeObserver and
    // therefore doesn't write its updates to disk ¯\_ (ツ)_/¯.
    scope.setContexts(NAVIGATION_CONTEXT_KEY, null as Any?)
    lastPublished = null
  }

  /** Clears the nav context only if it's still the exact instance this helper published. */
  fun clearIfPublished(scope: IScope) {
    val published = lastPublished ?: return
    if (scope.contexts[NAVIGATION_CONTEXT_KEY] === published) {
      clear(scope)
    }
    lastPublished = null
  }

  fun republish(scope: IScope) {
    lastPublished?.let { scope.setContexts(NAVIGATION_CONTEXT_KEY, it) }
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

  /** The exact screen name instance this helper last published, if any. */
  private var lastScreenName: String? = null

  fun update(scope: IScope, currentEntry: NormalizedSentryBackStackEntry) {
    // Publish a private copy so clear() can tell it apart by identity, even when another observer
    // publishes an equal name backed by the same interned string (e.g., a route name literal).
    val screenName = String(currentEntry.name.toCharArray())
    scope.screen = screenName
    lastScreenName = screenName
  }

  /** Clears the screen name only if it's still the exact instance this helper published. */
  fun clear(scope: IScope) {
    val screenName = lastScreenName ?: return
    if (scope.screen === screenName) {
      scope.screen = null
    }
    lastScreenName = null
  }

  fun republish(scope: IScope) {
    lastScreenName?.let { scope.screen = it }
  }
}

/** A helper class for generating nav breadcrumbs. */
private class NavBreadcrumbs(private val scopes: IScopes) {

  fun emit(fromEntry: NormalizedSentryBackStackEntry?, toEntry: NormalizedSentryBackStackEntry) {
    val breadcrumb =
      Breadcrumb().apply {
        type = NAVIGATION_OP
        category = NAVIGATION_OP

        fromEntry?.let { entry ->
          data["from"] = entry.name
          entry
            .argumentsWithMetadata()
            .takeIf { it.isNotEmpty() }
            ?.let { arguments ->
              data["from_arguments"] = arguments
            }
        }

        data["to"] = toEntry.name
        toEntry
          .argumentsWithMetadata()
          .takeIf { it.isNotEmpty() }
          ?.let { arguments ->
            data["to_arguments"] = arguments
          }

        level = INFO
      }

    val hint = Hint()
    scopes.addBreadcrumb(breadcrumb, hint)
  }
}

/**
 * Coordinates [BackStackObserver] updates to a shared [IScopes] instance.
 *
 * The observer that most recently [claimed the scope][claimScope] is active. When the active
 * observer [releases the scope][releaseScope], the coordinator hands the shared nav state to the
 * most recent claimant it still tracks.
 */
// TODO ADAM: Tests.
internal class NavScopesCoordinator private constructor() {

  /**
   * The observers that have [claimed the scope][claimScope] and have yet to release it or be gc'd.
   *
   * Stored in claim order, i.e., the active observer is at the end of the list.
   */
  private val trackedObservers = mutableListOf<WeakReference<BackStackObserver<*>>>()

  /**
   * Makes [observer] the active observer, replacing the current one if needed.
   *
   * A claim always succeeds, and the most recent claim wins. The previously active observer isn't
   * notified; it finds out via [ownsScope] and holds its changes until it becomes active again. The
   * claiming observer is responsible for publishing its own state.
   */
  fun claimScope(observer: BackStackObserver<*>) {
    trackedObservers.removeAll { it.get() == null || it.get() === observer }
    trackedObservers.add(WeakReference(observer))
  }

  /** Whether [observer] is the active observer, i.e., the most recent claimant still tracked. */
  fun ownsScope(observer: BackStackObserver<*>): Boolean {
    trackedObservers.removeAll { it.get() == null }
    return trackedObservers.lastOrNull()?.get() === observer
  }

  /**
   * Stops tracking [observer]. If it was the active observer, the most recent claimant still
   * tracked becomes active and [republishes its state][BackStackObserver.onHandoff].
   *
   * Releasing the scope from an inactive observer has no effect on the active one.
   */
  fun releaseScope(observer: BackStackObserver<*>) {
    val ownedScope = ownsScope(observer)
    trackedObservers.removeAll { it.get() == null || it.get() === observer }
    if (ownedScope) {
      trackedObservers.lastOrNull()?.get()?.onHandoff()
    }
  }

  companion object {

    // Neither the coordinator nor its weak observer references retain the scopes key.
    private val coordinators = WeakHashMap<IScopes, NavScopesCoordinator>()

    fun create(scopes: IScopes): NavScopesCoordinator =
      synchronized(coordinators) { coordinators.getOrPut(scopes) { NavScopesCoordinator() } }
  }
}
