package io.sentry.compose.navigation3

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.INFO
import io.sentry.SentryLevel.WARNING
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.compose.navigation3.SentryBackStackObserver.Companion.NAVIGATION_CONTEXT_KEY
import io.sentry.protocol.App
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.TracingUtils
import java.lang.ref.WeakReference

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
  private var activeTransaction: ITransaction? = null

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

  // TODO ADAM: Update KDoc.
  /** Updates Sentry state from the live single backstack. */
  internal fun onBackStackChanged(backStack: List<T>) {
    guard("onBackStackChanged") {
      scopes.apply {
        updateNavigationContext(backStack)
        handleTopEntry(backStack)
      }
    }
  }

  internal fun cleanup() {
    scopes.stopTracing()

    previousBackStackEntry = null

    if (captureBackStack) {
      scopes.configureScope { scope -> scope.removeContexts(NAVIGATION_CONTEXT_KEY) }
    }
  }

  // TODO ADAM: Rename.
  // TODO ADAM: KDoc.
  /**
   * TODO ADAM: Update. If the incoming backstack entry changed:
   * - starts a new transaction, and
   * - adds a breadcrumb
   * - refreshes screen tracking
   *
   * If the incoming backstack entry is the same:
   * - refreshes screen tracking
   */
  private fun IScopes.handleTopEntry(backStack: List<T>) {
    val currentTop: T = backStack.lastOrNull() ?: return

    val previousTop = previousBackStackEntry?.get()
    if (previousTop != null && previousTop == currentTop) {
      updateScreenTracking(currentTop)
      return
    }

    val routeName = resolveRouteName(currentTop)
    val arguments = resolveArguments(currentTop)

    this.addBreadcrumb(fromEntry = previousTop, toEntry = currentTop, routeName, arguments)
    this.updateScreenTracking(currentTop)
    this.startTracing(routeName, arguments)
    previousBackStackEntry = WeakReference(currentTop)
  }

  private fun IScopes.addBreadcrumb(
    fromEntry: T?,
    toEntry: T,
    routeName: String,
    arguments: Map<String, Any?>,
  ) {
    if (!enableNavigationBreadcrumbs) {
      return
    }

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

  private fun IScopes.updateScreenTracking(backStackEntry: T) {
    if (!this.options.isEnableScreenTracking) {
      return
    }

    val primaryRoute = resolveRouteName(backStackEntry)

    this.configureScope { scope ->
      scope.screen = primaryRoute
      val contexts = scope.contexts
      val app = contexts.app ?: App().also { contexts.setApp(it) }
      app.viewNames = listOf(primaryRoute)
    }
  }

  /**
   * Starts a route-scoped navigation transaction, or rotates trace context if transactions are
   * disabled.
   */
  private fun IScopes.startTracing(routeName: String, arguments: Map<String, Any?>) {
    if (!areNavigationTransactionsEnabled) {
      // Even without a route-scoped navigation transaction, advance the trace so work after this
      // navigation doesn't stay attached to the previous route's trace context.
      TracingUtils.startNewTrace(this)
      return
    }

    // Create a clean slate before starting a new transaction.
    this.stopTracing()

    val transactionOptions =
      TransactionOptions().also {
        it.isWaitForChildren = true
        it.idleTimeout = scopes.options.idleTimeout

        val deadlineTimeoutMillis = scopes.options.deadlineTimeout
        it.deadlineTimeout = if (deadlineTimeoutMillis <= 0) null else deadlineTimeoutMillis

        it.isTrimEnd = true
      }

    val transaction =
      this.startTransaction(
        TransactionContext(routeName, TransactionNameSource.ROUTE, NAVIGATION_OP),
        transactionOptions,
      )

    // Track the transaction immediately so a later failure can never orphan it.
    activeTransaction = transaction

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
   * Finishes the active navigation transaction and clears it from the scope if it is still bound.
   */
  private fun IScopes.stopTracing() {
    val transaction = activeTransaction ?: return
    val status = transaction.status ?: SpanStatus.OK
    transaction.finish(status)

    this.configureScope { scope ->
      scope.withTransaction { tx ->
        if (tx == transaction) {
          scope.clearTransaction()
        }
      }
    }

    activeTransaction = null
  }

  /**
   * Updates the receiver's [navigation context][NAVIGATION_CONTEXT_KEY] based on the incoming
   * [backStack].
   */
  private fun IScopes.updateNavigationContext(backStack: List<T>) {
    // Atm navigation context consists solely of backstack tracking. If it's disabled, clear any
    // previously captured context.
    if (!captureBackStack) {
      this.configureScope { scope -> scope.removeContexts(NAVIGATION_CONTEXT_KEY) }
      return
    }

    val entries = backStack.toRouteEntries()
    if (entries.isEmpty()) {
      this.configureScope { scope -> scope.removeContexts(NAVIGATION_CONTEXT_KEY) }
    } else {
      this.configureScope { scope ->
        scope.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("backstack" to entries))
      }
    }
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
