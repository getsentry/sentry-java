package io.sentry.compose.navigation3

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.compose.navigation3.BackStackConverter.RetentionPolicy
import io.sentry.compose.navigation3.PreparedChange.BackStackHasNewTop
import io.sentry.compose.navigation3.PreparedChange.BackStackHasSameTop
import io.sentry.compose.navigation3.PreparedChange.BackStackIsEmpty
import io.sentry.compose.navigation3.lifecycle.INavScope
import io.sentry.compose.navigation3.lifecycle.INavScopes
import io.sentry.compose.navigation3.lifecycle.NAVIGATION_CONTEXT_KEY
import io.sentry.compose.navigation3.lifecycle.NAVIGATION_OP
import io.sentry.compose.navigation3.lifecycle.NavTransactionRequest
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import java.lang.ref.WeakReference

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
 * Referential equality (===) is used to determine whether the top of the incoming back stack has
 * changed. That approach:
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
 * [dispose] (e.g., via invocation from an `*Effect` or another form of thread confinement).
 */
@Suppress("TooManyFunctions")
internal class BackStackObserver<T : Any>(
  private val scopes: INavScopes,
  private val options: SentryNavOptions,
  entryMapper: ForwardingBackStackEntryMapper<T>,
) {

  private val backStackConverter = BackStackConverter(entryMapper, scopes.options.logger)

  private val navBreadcrumbs = NavBreadcrumbs(scopes)
  private val navScreen = NavScreen()
  private val navContext = NavContext(options)

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
   * Updates Sentry nav state based on the provided [backStack].
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

  /**
   * Clears the nav state this observer recorded.
   *
   * Call it when observation ends so that no stale screen, nav context, or nav transaction outlives
   * it.
   */
  internal fun dispose() {
    scopes.configureScope { scope -> tearDown(scope) }
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

  private fun applyChange(scope: INavScope, change: PreparedChange<T>) {
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
    scope: INavScope,
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

    scope.finishNavTransaction()

    if (areNavigationTransactionsEnabled) {
      scope.startNavTransaction(navTransactionRequestFor(currentBackStack))
    } else {
      scope.rotatePropagationContext()
    }

    storePreviousTop(currentBackStack)
  }

  private fun handleSameTop(scope: INavScope, backStack: BackStackData<T>) {
    navContext.update(scope, backStack.capturedEntriesNormalized)

    // Keeps the nav context of transactions started later for the same destination (e.g., when the
    // user returns to the nav root) in step with the back stack.
    if (areNavigationTransactionsEnabled) {
      scope.updateNavTransactionRequest(navTransactionRequestFor(backStack))
    }

    storePreviousTop(backStack)
  }

  private fun handleEmptyBackStack(scope: INavScope) {
    tearDown(scope)
  }

  private fun storePreviousTop(backStack: BackStackData<T>) {
    previousTop = WeakReference(backStack.topEntry)
    previousTopNormalized = backStack.topEntryNormalized
  }

  private fun clearPreviousTop() {
    previousTop = null
    previousTopNormalized = null
  }

  private fun navTransactionRequestFor(backStack: BackStackData<T>): NavTransactionRequest {
    val top = backStack.topEntryNormalized

    return NavTransactionRequest.forDestination(
      name = top.name,
      arguments = top.argumentsWithMetadata(),
      contexts = navContext.forTransaction(backStack.capturedEntriesNormalized),
      viewNames = if (scopes.options.isEnableScreenTracking) listOf(top.name) else null,
      options = scopes.options,
    )
  }

  private fun tearDown(scope: INavScope) {
    scope.finishNavTransaction()
    navContext.clear(scope)
    navScreen.clear(scope)
    clearPreviousTop()
  }
}

/**
 * A model for applying one back stack update.
 *
 * Lets us separate change preparation from its application so that the [INavScopes.configureScope]
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

/** A helper class for updating [nav context][NAVIGATION_CONTEXT_KEY]. */
private class NavContext(private val options: SentryNavOptions) {

  private companion object {
    private const val BACKSTACK_KEY = "Back Stack"
  }

  fun update(scope: INavScope, backStackEntries: List<NormalizedSentryBackStackEntry>) {
    if (backStackEntries.isEmpty()) {
      clear(scope)
      return
    }

    scope.setNavigationContext(backStackEntries.toBackStackMap())
  }

  fun clear(scope: INavScope) {
    scope.setNavigationContext(null)
  }

  /**
   * The nav context to set on the nav transaction for a destination whose back stack is
   * [capturedEntries], if any.
   *
   * Needed because transactions inherit base scope context on a per-key basis unless transactions
   * have their own values for those keys. In our case, we need to keep fresh back stack values in
   * the base context for purposes of crash reporting. But those values will often advance past
   * what's relevant to a given transaction. Setting the destination's own back stack on its
   * transaction prevents misassociation.
   */
  fun forTransaction(capturedEntries: List<NormalizedSentryBackStackEntry>): Map<String, Any> =
    if (options.captureBackStack && capturedEntries.isNotEmpty()) {
      mapOf(NAVIGATION_CONTEXT_KEY to capturedEntries.toBackStackMap())
    } else {
      emptyMap()
    }

  /** Builds the `{"backstack": [...]}` map bound under [NAVIGATION_CONTEXT_KEY]. */
  private fun List<NormalizedSentryBackStackEntry>.toBackStackMap(): Map<String, Any?> =
    mapOf(BACKSTACK_KEY to serialize())
}

/** A helper class for updating the tracked screen name. */
private class NavScreen {

  private var lastScreenName: String? = null

  fun update(scope: INavScope, currentEntry: NormalizedSentryBackStackEntry) {
    scope.screen = currentEntry.name
    lastScreenName = currentEntry.name
  }

  fun clear(scope: INavScope) {
    val screenName = lastScreenName ?: return
    if (scope.screen == screenName) {
      scope.screen = null
    }
    lastScreenName = null
  }
}

/** A helper class for generating nav breadcrumbs. */
private class NavBreadcrumbs(private val scopes: INavScopes) {

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
