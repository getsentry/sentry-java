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
import io.sentry.util.ExceptionUtils.rethrowIfFatal
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import java.util.IdentityHashMap

/*
 * TODO ADAM: NEXT STEPS
 *
 * - Apply Compose performance rendering tracing to Nav2Activity and Nav3Activity. (We want to ensure those traces show up in the nav transactions.)
 *
 * - Make sure our composition ordering updates work correctly when creating a nav transaction. (Eg, if we navigate to a screen that uses a LaunchedEffect to do initial work, we want to make sure that work is tracked under the nav transaction.)
 *
 * --- Determine whether we want to use the snapshot-based approach or the previous DisposableEffect approach. (See discussion of downsides of snapshot approach in ~/Desktop/nav3-observing-sync-work-in-destination-composable.txt)
 *
 * --- Any performance concerns now that we're no longer using a LaunchedEffect to call onBackstackChanged()?
 *
 * --- Explore i) ordering SentryNavEffect vs NavDisplay, ii) SentryNavDisplay, or iii) rememberSentryNav3BackStack() (see "SentryNavEffect Ordering Relative to NavDisplay" section in ~/Desktop/nav2-vs-nav3-transaction-policies.txt).
 *
 * ------ Note that remember*() and side effects have different semantics in Compose / are executed at different points in the composition lifecycle.
 *
 * --- Add simulated work in the destination that involves LaunchedEffect, DisposableEffect, etc.
 *
 * --- Have LLM check via Sample App.
 *
 * --- Decide whether we want to introduce SentryNavDecorator in phase 1 to ensure ordering updates work correctly.
 *
 * - Final API decision: SentryNavEffect vs (a virtually identical) rememberSentry[Nav3]BackStack() vs SentryNavDisplay
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
/**
 * Records Sentry state for one [SentryNavEffect].
 *
 * This class is not thread-safe. Its owning composition must serialize calls to
 * [onBackStackChanged] and [cleanup]; each effect remembers a separate state holder instance.
 */
@Suppress("TooManyFunctions")
internal class SentryNavStateHolder<T : Any> internal constructor(private val scopes: IScopes) {

  private var lastBackStackContext: List<Map<String, Any?>>? = null
  private var lastDestination: Destination<T>? = null
  private var lastScreenRouteName: String? = null

  private var activeNav3Transaction: ITransaction? = null

  init {
    addIntegrationToSdkVersion("ComposeNavigation3")
  }

  internal companion object {

    private const val NAVIGATION_CONTEXT_KEY = "navigation"
    private const val NAVIGATION_OP: String = "navigation"
    private const val TRANSACTION_ORIGIN = "auto.navigation.nav3"
    private const val MAX_ARGUMENT_DEPTH = 20
    private const val MAX_ARGUMENT_VALUES = 1_000

    init {
      SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-android-navigation3", BuildConfig.VERSION_NAME)
    }
  }

  /**
   * Updates recorded Sentry data based on the provided [backStack].
   *
   * This method is idempotent and is safe to call on every recomposition. Extractors are the source
   * of truth for route names and arguments, so they must run whenever this method runs; extractor
   * output can change even when the backstack, extractor instances, and options are unchanged.
   */
  internal fun onBackStackChanged(
    backStack: List<T>,
    options: SentryNavOptions,
    nameExtractor: ((T) -> String)?,
    argumentsExtractor: ((T) -> Map<String, Any?>)?,
  ) {
    guard("onBackStackChanged") {
      val capturedEntries =
        if (options.captureBackStack) {
          backStack.takeLast(options.maxCapturedBackStackEntries).asReversed()
        } else {
          emptyList()
        }
      val currentEntry =
        if (options.captureBackStack) capturedEntries.firstOrNull() else backStack.lastOrNull()
      val areNavigationTransactionsEnabled =
        scopes.options.isTracingEnabled && options.enableNavigationTransactions

      if (currentEntry == null) {
        scopes.configureScope { scope ->
          if (options.captureBackStack) {
            scope.updateNavigationContextIfChanged(emptyList())
          } else {
            scope.clearNavigationContextIfNeeded()
          }

          scope.stopNav3Transaction()
          scope.clearEntryAsScreen()
          lastDestination = null
        }
        return@guard
      }

      // Resolve all caller-controlled route data before entering configureScope. Its production
      // implementation catches Throwable, which would otherwise swallow fatal errors rethrown by
      // the extractor guards below.
      val previousDestination = lastDestination
      val argumentSanitizationState = ArgumentSanitizationState()
      val currentRouteName = resolveRouteName(currentEntry, nameExtractor)
      val destinationChanged = previousDestination?.entry != currentEntry
      val screenChanged = previousDestination?.routeName != currentRouteName

      val currentDestination =
        Destination(
          entry = currentEntry,
          routeName = currentRouteName,
          arguments = resolveArguments(currentEntry, argumentsExtractor, argumentSanitizationState),
        )
      val backStackContext =
        if (options.captureBackStack) {
          resolveBackStackContext(
            capturedEntries,
            currentDestination,
            nameExtractor,
            argumentsExtractor,
            argumentSanitizationState,
          )
        } else {
          emptyList()
        }

      scopes.configureScope { scope ->
        if (options.captureBackStack) {
          scope.updateNavigationContextIfChanged(backStackContext)
        } else {
          scope.clearNavigationContextIfNeeded()
        }

        if (!areNavigationTransactionsEnabled) {
          scope.stopNav3Transaction()
        }

        if (scopes.options.isEnableScreenTracking) {
          if (destinationChanged || screenChanged) {
            scope.trackRouteAsScreen(currentDestination.routeName)
          }
        }

        if (!destinationChanged) {
          lastDestination = currentDestination
          return@configureScope
        }

        if (options.enableNavigationBreadcrumbs) {
          scopes.addNav3Breadcrumb(
            previousDestination = previousDestination,
            currentDestination = currentDestination,
          )
        }

        // Refresh nav tracing (i.e., finish any previous nav3 transaction and start a new one if
        // enabled + no other transaction is active).
        if (areNavigationTransactionsEnabled) {
          scope.stopNav3Transaction()
          scopes.startNav3Transaction(currentDestination.routeName, currentDestination.arguments)
        } else {
          // Keep error grouping identical whether or not nav transactions are enabled.
          scope.rotatePropagationContext()
        }

        lastDestination = currentDestination
      }
    }
  }

  private fun resolveBackStackContext(
    capturedEntries: List<T>,
    currentDestination: Destination<T>,
    nameExtractor: ((T) -> String)?,
    argumentsExtractor: ((T) -> Map<String, Any?>)?,
    argumentSanitizationState: ArgumentSanitizationState,
  ): List<Map<String, Any?>> =
    buildList(capturedEntries.size) {
      capturedEntries.forEachIndexed { index, entry ->
        val routeName =
          if (index == 0) currentDestination.routeName else resolveRouteName(entry, nameExtractor)
        val arguments =
          when {
            index == 0 -> currentDestination.arguments
            argumentSanitizationState.isValueBudgetExceeded() -> emptyMap()
            else -> resolveArguments(entry, argumentsExtractor, argumentSanitizationState)
          }
        add(routeContext(routeName, arguments))
      }
    }

  internal fun cleanup() {
    val shouldRemoveNavigationContext = lastBackStackContext != null

    lastDestination = null
    lastBackStackContext = null

    scopes.configureScope { scope ->
      scope.stopNav3Transaction()
      scope.clearEntryAsScreen()
      // This state holder owns the Nav3 navigation context while it's in the composition, and
      // cleanup
      // removes it to avoid leaking stale backstack data after observation stops. If the host app
      // replaces one state holder with another, there may be a brief gap where events lack
      // navigation context. Apps should keep the state holder at the nav root so cleanup only runs
      // when the
      // navigation session is ending, not during normal destination changes.
      if (shouldRemoveNavigationContext) {
        scope.removeContexts(NAVIGATION_CONTEXT_KEY)
      }
    }
  }

  private fun IScope.updateNavigationContextIfChanged(backStackContext: List<Map<String, Any?>>) {
    if (lastBackStackContext == backStackContext) {
      return
    }

    if (backStackContext.isEmpty()) {
      this.removeContexts(NAVIGATION_CONTEXT_KEY)
    } else {
      this.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("backstack" to backStackContext))
    }
    lastBackStackContext = backStackContext
  }

  private fun IScope.clearNavigationContextIfNeeded() {
    if (lastBackStackContext != null) {
      this.removeContexts(NAVIGATION_CONTEXT_KEY)
      lastBackStackContext = null
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
  private fun IScope.trackRouteAsScreen(routeName: String) {
    this.screen = routeName
    val scopeContexts = this.contexts
    val app = scopeContexts.app ?: App().also { scopeContexts.setApp(it) }
    app.viewNames = listOf(routeName)
    lastScreenRouteName = routeName
  }

  private fun IScope.clearEntryAsScreen() {
    val routeName = lastScreenRouteName ?: return
    if (this.screen == routeName) {
      this.screen = null
    }
    if (this.contexts.app?.viewNames == listOf(routeName)) {
      this.contexts.app?.viewNames = null
    }
    lastScreenRouteName = null
  }

  private fun IScopes.addNav3Breadcrumb(
    previousDestination: Destination<T>?,
    currentDestination: Destination<T>,
  ) {
    val breadcrumb =
      Breadcrumb().apply {
        type = NAVIGATION_OP
        category = NAVIGATION_OP

        previousDestination?.let { previous ->
          data["from"] = previous.routeName
          if (previous.arguments.isNotEmpty()) {
            data["from_arguments"] = previous.arguments
          }
        }

        data["to"] = currentDestination.routeName
        if (currentDestination.arguments.isNotEmpty()) {
          data["to_arguments"] = currentDestination.arguments
        }

        level = INFO
      }

    val hint = Hint()
    hint.set(TypeCheckHint.NAV3_DESTINATION, currentDestination.entry)
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

  private data class Destination<T : Any>(
    val entry: T,
    val routeName: String,
    val arguments: Map<String, Any?>,
  )

  private fun routeContext(routeName: String, arguments: Map<String, Any?>): Map<String, Any?> =
    buildMap {
      put("route", routeName)
      if (arguments.isNotEmpty()) {
        put("args", arguments)
      }
    }

  // TODO ADAM: VisibleForTesting or private.
  @Suppress("TooGenericExceptionCaught")
  internal fun resolveRouteName(
    backStackEntry: T,
    nameExtractor: ((T) -> String)? = null,
  ): String {
    val name =
      try {
        nameExtractor?.invoke(backStackEntry)
      } catch (t: Throwable) {
        rethrowIfFatal(t)
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
  private fun resolveArguments(
    backStackEntry: T,
    argumentsExtractor: ((T) -> Map<String, Any?>)?,
    state: ArgumentSanitizationState,
  ): Map<String, Any?> {
    val raw =
      try {
        argumentsExtractor?.invoke(backStackEntry) ?: return emptyMap()
      } catch (t: Throwable) {
        rethrowIfFatal(t)
        scopes.options.logger.log(
          WARNING,
          "Nav3 argumentsExtractor threw while resolving arguments. Skipping arguments.",
          t,
        )
        return emptyMap()
      }

    return try {
      sanitizeArguments(raw, state)
    } catch (_: ArgumentValueBudgetExceededException) {
      scopes.options.logger.log(
        WARNING,
        "Nav3 arguments exceeded the maximum total value count for one backstack update. " +
          "Skipping arguments for this and older captured entries.",
      )
      emptyMap()
    } catch (_: ArgumentStructureException) {
      scopes.options.logger.log(
        WARNING,
        "Nav3 argument sanitization failed (possibly a cyclic or deeply nested structure). " +
          "Skipping arguments.",
      )
      emptyMap()
    } catch (t: Throwable) {
      rethrowIfFatal(t)
      scopes.options.logger.log(
        WARNING,
        "Nav3 argument sanitization failed (possibly a cyclic or deeply nested structure). " +
          "Skipping arguments.",
        t,
      )
      emptyMap()
    }
  }

  private fun sanitizeArguments(
    args: Map<String, Any?>,
    state: ArgumentSanitizationState,
  ): Map<String, Any?> {
    return sanitizeMap(args, state, depth = 0)
  }

  private fun sanitizeValue(
    value: Any?,
    state: ArgumentSanitizationState,
    depth: Int,
  ): Any? {
    state.visit(depth)
    return when (value) {
      null,
      is String,
      is Number,
      is Boolean -> value

      is Map<*, *> -> sanitizeMap(value, state, depth)
      is Collection<*> -> sanitizeCollection(value, state, depth)
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

  private fun sanitizeMap(
    value: Map<*, *>,
    state: ArgumentSanitizationState,
    depth: Int,
  ): Map<String, Any?> {
    state.enter(value)
    try {
      val sanitized = LinkedHashMap<String, Any?>()
      for ((key, childValue) in value) {
        sanitized[key.toString()] = sanitizeValue(childValue, state, depth + 1)
      }
      return sanitized
    } finally {
      state.exit(value)
    }
  }

  private fun sanitizeCollection(
    value: Collection<*>,
    state: ArgumentSanitizationState,
    depth: Int,
  ): List<Any?> {
    state.enter(value)
    try {
      val sanitized = ArrayList<Any?>()
      for (childValue in value) {
        sanitized += sanitizeValue(childValue, state, depth + 1)
      }
      return sanitized
    } finally {
      state.exit(value)
    }
  }

  private class ArgumentSanitizationState {
    private val activeContainers = IdentityHashMap<Any, Unit>()
    private var valueCount = 0
    private var valueBudgetExceeded = false

    fun visit(depth: Int) {
      if (depth > MAX_ARGUMENT_DEPTH) {
        throw ArgumentStructureException("Nav3 arguments exceed the maximum depth")
      }
      if (++valueCount > MAX_ARGUMENT_VALUES) {
        valueBudgetExceeded = true
        throw ArgumentValueBudgetExceededException(
          "Nav3 arguments exceed the maximum total value count for one backstack update"
        )
      }
    }

    fun enter(container: Any) {
      if (activeContainers.put(container, Unit) != null) {
        throw ArgumentStructureException("Nav3 arguments contain a cyclic reference")
      }
    }

    fun exit(container: Any) {
      activeContainers.remove(container)
    }

    fun isValueBudgetExceeded(): Boolean = valueBudgetExceeded
  }

  private open class ArgumentSanitizationException(message: String) :
    IllegalArgumentException(message)

  private class ArgumentStructureException(message: String) : ArgumentSanitizationException(message)

  private class ArgumentValueBudgetExceededException(message: String) :
    ArgumentSanitizationException(message)

  // TODO ADAM: Avoid context of use reference.
  /**
   * Runs instrumentation callbacks safely so a misbehaving host key type can never crash the app.
   */
  @Suppress("TooGenericExceptionCaught")
  private inline fun guard(operation: String, body: () -> Unit) {
    try {
      body()
    } catch (t: Throwable) {
      rethrowIfFatal(t)
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
