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
import io.sentry.SentryLevel.WARNING
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.protocol.App
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import java.lang.ref.WeakReference

/*
 * TODO ADAM: NEXT STEPS
 *
 * - Apply Compose performance rendering tracing to Nav2Activity and Nav3Activity. (We want to ensure those traces show up in the nav transactions.)
 *
 * - Make sure our composition ordering updates work correctly when creating a nav transaction. (Eg, if we navigate to a screen that uses a LaunchedEffect to do initial work, we want to make sure that work is tracked under the nav transaction.)
 *
 * --- Sanity check the current solution: Does Compose guarantee it recomposes / re-executes observers of changed state (here, the backstack) in lexical order?
 *
 * --- Any performance concerns now that we're no longer using a LaunchedEffect to call onBackstackChanged()?
 *
 * --- Explore i) ordering SentryNav3Effect vs NavDisplay, ii) SentryNavDisplay, or iii) rememberSentryNav3BackStack() (see "SentryNav3Effect Ordering Relative to NavDisplay" section in ~/Desktop/nav2-vs-nav3-transaction-policies.txt).
 *
 * ------ Note that remember*() and side effects have different semantics in Compose / are executed at different points in the composition lifecycle.
 *
 * --- Add simulated work in the destination that involves LaunchedEffect, DisposableEffect, etc.
 *
 * --- Have LLM check via Sample App.
 *
 * --- Decide whether we want to introduce SentryNavDecorator in phase 1 to ensure ordering updates work correctly.
 *
 * - Final API decision: SentryNav3Effect vs (a virtually identical) rememberSentry[Nav3]BackStack() vs SentryNavDisplay
 *
 * - Make sure sample app contains all required nav3 recipes.
 *
 * - Have LLM re-check for Nav2 vs Nav3 parity.
 *
 * - Fix sample app UX wonkiness (eg, tab selection, etc.)
 *
 * - Use kotlinx serialization to produce routes in sample app.
 *
 * - Determine whether we want to emit any additional Sentry state when the backstack is updated (eg, SceneStrategy, DialogStrategy, etc.).
 *
 * - Harmonize Nav2 and Nav3 sample apps.
 *
 * - Initial PRs for Nav2 sample app without Compose tab, and then Compose tab.
 *
 * - Then PR for Nav3 phase 1 implementation.
 *
 * - Does SAGP auto-instrument for Nav2??
 */
/*
 * TODO ADAM: MAJOR DISCUSSION POINTS
 *
 * - Transaction generation: Updating our Activity-centry ui.load approach to a single-Activity, Compose-first world.
 *
 * --- Transaction deference policies. Right now nav transactions defer to any existing transaction, which will often be ui.load and (if enabled) ui.action.
 * --- See note from convo with Geno.
 * --- See ~/Desktop/nav2-vs-nav3-transaction-policies.txt and ~/Desktop/automatic-transactions.txt
 *
 * - Transaction UX in Sentry UI:
 *
 * --- Confusing to have, say, a navigation transaction whose only child span is a transient one occurring, say, at 2.99 seconds. That means the nav transaction looks like it takes ~3 seconds, even though there may only be 0.01 ms of work performed.
 * ------ Transactions are containers, not proper spans. Our UX should indicate as much.
 */

// TODO ADAM: KDoc
internal class SentryBackStackObserver<T : Any>
internal constructor(
  private val scopes: IScopes,
  private val enableNavigationBreadcrumbs: Boolean,
  private val enableNavigationTransactions: Boolean,
  private val captureBackStack: Boolean,
  private val maxCapturedBackStackEntries: Int,
  nameExtractor: ((T) -> String)?,
  argumentsExtractor: ((T) -> Map<String, Any?>)?,
) {

  internal var nameExtractor: ((T) -> String)? = nameExtractor
  internal var argumentsExtractor: ((T) -> Map<String, Any?>)? = argumentsExtractor

  private var previousBackStackEntry: WeakReference<T>? =
    null // TODO ADAM: Memory leak due diligence

  private var activeNav3Transaction: ITransaction? = null

  private val areNavigationTransactionsEnabled: Boolean
    get() = scopes.options.isTracingEnabled && enableNavigationTransactions

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
   * // TODO ADAM: Note that we don't appear to be doing this atm given the observer key in our
   * DisposableEffect. Note: This method is **not** idempotent. Callers should protect against
   * repeat invocations with the same back stack.
   */
  internal fun onBackStackChanged(backStack: List<T>) {
    guard("onBackStackChanged") {
      scopes.configureScope { scope ->
        // Always update the recorded backstack.
        scope.updateNavigationContext(backStack)

        // Return early if the top of the backstack is the same...
        val previousTop: T? = previousBackStackEntry?.get()
        val currentTop: T = backStack.lastOrNull() ?: return@configureScope
        if (previousTop == currentTop) {
          return@configureScope
        }

        // ...otherwise record data relevant to a new nav destination.
        val routeName = resolveRouteName(currentTop)
        val arguments = resolveArguments(currentTop)

        if (scopes.options.isEnableScreenTracking) {
          scope.trackEntryAsScreen(backStackEntry = currentTop)
        }

        if (enableNavigationBreadcrumbs) {
          scopes.addNav3Breadcrumb(
            fromEntry = previousTop,
            toEntry = currentTop,
            routeName,
            arguments,
          )
        }

        // Refresh nav tracing (i.e., finish any previous nav3 transaction and start a new one if
        // enabled + no other transaction is active).
        scope.stopNav3Transaction()

        if (areNavigationTransactionsEnabled) {
          scopes.startNav3Transaction(routeName, arguments)
        } else {
          // Keep error grouping identical whether or not nav transactions are enabled.
          scope.rotatePropagationContext()
        }

        previousBackStackEntry = WeakReference(currentTop)
      }
    }
  }

  internal fun cleanup() {
    previousBackStackEntry = null

    scopes.configureScope { scope ->
      scope.stopNav3Transaction()

      if (captureBackStack) {
        // This observer owns the Nav3 navigation context while it's in the composition, and cleanup
        // removes it to avoid leaking stale backstack data after observation stops. If the host app
        // replaces one observer with another, there may be a brief gap where events lack navigation
        // context. Apps should keep the observer at the nav root so cleanup only runs when the
        // navigation session is ending, not during normal destination changes.
        scope.removeContexts(NAVIGATION_CONTEXT_KEY)
      }
    }
  }

  private fun IScope.updateNavigationContext(backStack: List<T>) {
    // Atm navigation context consists solely of backstack tracking. If it's disabled, clear any
    // previously captured context.
    if (!captureBackStack) {
      this.removeContexts(NAVIGATION_CONTEXT_KEY)
      return
    }

    val entries = backStack.toRouteEntries()
    if (entries.isEmpty()) {
      this.removeContexts(NAVIGATION_CONTEXT_KEY)
    } else {
      this.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("backstack" to entries))
    }
  }

  private fun IScope.rotatePropagationContext() {
    withPropagationContext { setPropagationContext(PropagationContext()) }
  }

  /**
   * Tracks the provided [backStackEntry] as the current "screen" (i.e., visible UI).
   *
   * Update as needed once multipane and multiple back stack support are introduced.
   */
  private fun IScope.trackEntryAsScreen(backStackEntry: T) {
    val primaryRoute = resolveRouteName(backStackEntry)

    this.screen = primaryRoute
    val scopeContexts = this.contexts
    val app = scopeContexts.app ?: App().also { scopeContexts.setApp(it) }
    app.viewNames = listOf(primaryRoute)
  }

  private fun IScopes.addNav3Breadcrumb(
    fromEntry: T?,
    toEntry: T,
    routeName: String,
    arguments: Map<String, Any?>,
  ) {
    val breadcrumb =
      Breadcrumb().apply {
        type = NAVIGATION_OP
        category = NAVIGATION_OP

        fromEntry?.let { prev ->
          data["from"] = resolveRouteName(prev)
          val fromArgs = resolveArguments(prev)
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

  /**
   * Starts an idle navigation transaction with the receiver and sets it as [activeNav3Transaction],
   * or no-ops if a transaction isn't needed (e.g., because an ambient transaction already exists).
   *
   * Note: By convention, sentry-java integrations capable of producing transactions bound to the
   * ambient scope do so only if there's no already-bound transaction or other current span context
   * (e.g., OTel spans installed via `IScope.setActiveSpan()`). That's especially important in the
   * case of navigation, which would tend to clobber `ui.load` transactions and distort TTID and
   * TTFD metrics in users' Mobile Vitals dashboards.
   */
  private fun IScopes.startNav3Transaction(routeName: String, arguments: Map<String, Any?>) {
    if (this.span != null) {
      this.options.logger.log(
        DEBUG,
        "Nav3 transaction for route %s won't be created because another transaction or span is active.",
        routeName,
      )

      // Note that we don't create a fallback navigation span in situations where a parent
      // transaction isn't needed. Doing so would let us consistently provide users with spans
      // marking nav updates, but we don't bother b/c i) we want to maintain parity with our Nav2
      // integration and ii) the "marker" spans would consume quota without providing much value.
      // (Users can rely on breadcrumbs instead.)
      return
    }

    val transactionOptions =
      TransactionOptions().also {
        it.isWaitForChildren = true
        it.idleTimeout = this.options.idleTimeout
        val deadlineTimeoutMillis = this.options.deadlineTimeout
        it.deadlineTimeout = if (deadlineTimeoutMillis <= 0) null else deadlineTimeoutMillis
        it.isTrimEnd = true
      }

    val transaction =
      this.startTransaction(
        TransactionContext(routeName, TransactionNameSource.ROUTE, NAVIGATION_OP),
        transactionOptions,
      )

    // Track the transaction immediately so a later failure can never orphan it.
    activeNav3Transaction = transaction

    transaction.apply {
      spanContext.origin = TRANSACTION_ORIGIN
      if (arguments.isNotEmpty()) {
        setData("arguments", arguments)
      }
    }

    this.configureScope { scope ->
      scope.withTransaction { tx ->
        if (tx == null) {
          scope.transaction = transaction
        }
      }
    }
  }

  /**
   * Finishes and unsets [activeNav3Transaction], clearing it from the receiver if still bound.
   *
   * No-ops if [activeNav3Transaction] was never set.
   */
  private fun IScope.stopNav3Transaction() {
    val transaction = activeNav3Transaction ?: return
    val status = transaction.status ?: SpanStatus.OK
    transaction.finish(status)

    this.withTransaction { tx ->
      if (tx == transaction) {
        this.clearTransaction()
      }
    }

    activeNav3Transaction = null
  }

  // TODO ADAM: Provide examples
  /**
   * Converts the receiver into a serializable list of route entries.
   *
   * Each entry contains:
   * - "route": the normalized route name
   * - "args": optional sanitized route arguments
   */
  private fun List<T>.toRouteEntries(): List<Map<String, Any?>> =
    this.takeLast(maxCapturedBackStackEntries).asReversed().map { entry ->
      buildMap {
        put("route", resolveRouteName(entry))
        val args = resolveArguments(entry)
        if (args.isNotEmpty()) {
          put("args", args)
        }
      }
    }

  // TODO ADAM: VisibleForTesting or private.
  @Suppress("TooGenericExceptionCaught")
  internal fun resolveRouteName(backStackEntry: T): String {
    val name =
      try {
        nameExtractor?.invoke(backStackEntry)

        // TODO ADAM: Use Nelson's function rather than catching Throwable (passim).
      } catch (t: Throwable) {
        scopes.options.logger.log(
          WARNING,
          "Nav3 nameExtractor threw while resolving a route name. Falling back to class simpleName.",
          t,
        )
        null
      } ?: backStackEntry::class.simpleName ?: "unknown"

    return "/${name.removePrefix("/")}"
  }

  @Suppress("TooGenericExceptionCaught")
  private fun resolveArguments(backStackEntry: T): Map<String, Any?> {
    val raw =
      try {
        argumentsExtractor?.invoke(backStackEntry) ?: return emptyMap()
      } catch (t: Throwable) {
        scopes.options.logger.log(
          WARNING,
          "Nav3 argumentsExtractor threw while resolving arguments. Skipping arguments.",
          t,
        )
        return emptyMap()
      }

    return try {
      sanitizeArguments(raw)
    } catch (t: Throwable) {
      scopes.options.logger.log(
        WARNING,
        "Nav3 argument sanitization failed (possibly a cyclic or deeply nested structure). " +
          "Skipping arguments.",
        t,
      )
      emptyMap()
    }
  }

  private fun sanitizeArguments(args: Map<String, Any?>): Map<String, Any?> {
    val sanitized = LinkedHashMap<String, Any?>(args.size)
    for ((key, value) in args) {
      sanitized[key] = sanitizeValue(value)
    }
    return sanitized
  }

  private fun sanitizeValue(value: Any?): Any? {
    return when (value) {
      null,
      is String,
      is Number,
      is Boolean -> value

      is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to sanitizeValue(v) }
      is Collection<*> -> value.map { sanitizeValue(it) }
      else -> {
        scopes.options.logger.log(
          WARNING,
          "Nav3 argumentsExtractor returned non-primitive value of type %s for serialization. " +
            "Falling back to toString(). Use primitive types (String, Number, Boolean) for reliable results.",
          value::class.simpleName,
        )
        value.toString()
      }
    }
  }

  // TODO ADAM: Avoid context of use reference.
  /**
   * Runs instrumentation callbacks safely so a misbehaving host key type can never crash the app.
   */
  @Suppress("TooGenericExceptionCaught")
  private inline fun guard(operation: String, body: () -> Unit) {
    try {
      body()
    } catch (t: Throwable) {
      scopes.options.logger.log(
        WARNING, // TODO ADAM: Error level (passim)?
        t,
        "Nav3 instrumentation failed during %s (possibly a host key type whose equals/hashCode/" +
          "toString threw). Skipping this navigation update.", // TODO ADAM: key -> entry?
        operation,
      )
    }
  }
}
