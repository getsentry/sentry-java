package io.sentry.compose.navigation3

import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import io.sentry.util.ExceptionUtils
import java.util.IdentityHashMap
import org.jetbrains.annotations.TestOnly

/**
 * Translates app-defined back stack entries into input-ordered [Route]s.
 *
 * **Threading policy**
 *
 * This class performs work synchronously on the calling thread. Host-provided [extractors] are
 * invoked on that same thread and should remain small, non-blocking, and safe for the caller's
 * threading context.
 */
internal class RouteTranslator<T : Any>(
  private val extractors: () -> RouteExtractors<T>,
  private val logger: ILogger,
) {

  companion object {
    internal const val UNKNOWN_ROUTE_NAME = "/unknown"
  }

  /** Translates the provided [backStackEntries] into [Route]s and returns them in input order. */
  fun translate(backStackEntries: List<T>, retentionPolicy: RetentionPolicy): List<Route> {
    val warningState = WarningState()
    val sanitizer = ArgumentSanitizer(logger, warningState)

    val routes = MutableList<Route?>(backStackEntries.size) { null }
    val indicesInPolicyOrder =
      when (retentionPolicy) {
        RetentionPolicy.KEEP_FIRST -> backStackEntries.indices
        RetentionPolicy.KEEP_LAST -> backStackEntries.indices.reversed()
      }

    for (index in indicesInPolicyOrder) {
      val entry = backStackEntries[index]
      routes[index] =
        Route(
          name = extractRouteName(entry, warningState),
          arguments = extractRouteArguments(entry, sanitizer),
        )
    }

    return routes.requireNoNulls()
  }

  /**
   * Returns a route name for the provided [backStackEntry], based on this translator's
   * [name extractor][RouteExtractors.nameExtractor].
   *
   * The returned name is normalized to always include a leading slash. E.g., both `PromoDialog` and
   * `/PromoDialog` are resolved to `/PromoDialog`. (Doing so maintains parity with our Nav2
   * convention.)
   */
  @TestOnly
  @Suppress("TooGenericExceptionCaught")
  fun extractRouteName(backStackEntry: T, warningState: WarningState): String {
    val name: String? =
      try {
        extractors.invoke().getName(backStackEntry)
      } catch (t: Throwable) {
        // Route name extractors are host app callbacks.
        ExceptionUtils.rethrowIfFatal(t)
        warningState.logNameExtractorFailureWarning(logger, t)
        return UNKNOWN_ROUTE_NAME
      }

    val normalizedName = name?.trim()?.takeUnless { it.isEmpty() }?.removePrefix("/")
    if (normalizedName == null) {
      warningState.logInvalidRouteNameWarning(logger)
      return UNKNOWN_ROUTE_NAME
    }

    return "/$normalizedName"
  }

  /**
   * Returns the arguments for the provided [backStackEntry], based on this translator's
   * [arguments extractor][RouteExtractors.argumentsExtractor].
   *
   * The arguments are sanitized before being returned, i.e., bounded in size and depth, and
   * converted into a serializable form.
   */
  @TestOnly
  @Suppress("TooGenericExceptionCaught")
  fun extractRouteArguments(
    backStackEntry: T,
    sanitizer: ArgumentSanitizer,
  ): Map<String, Any?> {
    val raw =
      try {
        extractors.invoke().getArguments(backStackEntry) ?: return emptyMap()
      } catch (t: Throwable) {
        // Route argument extractors are host app callbacks.
        ExceptionUtils.rethrowIfFatal(t)
        logger.log(
          WARNING,
          "Nav3 argumentsExtractor threw while resolving arguments. Skipping arguments.",
          t,
        )
        return emptyMap()
      }

    return sanitizer.sanitizeEntry(raw)
  }

  /**
   * Specifies whether route info starting at the initial or final element of a back stack list
   * should be preserved if a size budget is exceeded.
   *
   * Most clients will want to select the policy that starts at the top of their back stack.
   */
  internal enum class RetentionPolicy {

    /**
     * Retains route info for lower indexed elements in the back stack list if a particular info
     * budget is reached. Retention starts at index 0 and increments until the budget is exhausted.
     */
    KEEP_FIRST,

    /**
     * Retains route info for higher indexed elements in the back stack list if a particular info
     * budget is reached. Retention starts at lastIndex and decrements until the budget is
     * exhausted.
     */
    KEEP_LAST,
  }

  /**
   * Sanitizes a back stack update's argument maps into a serializable form. It bounds depth and
   * total value count, and it rejects cyclic structures.
   *
   * One instance is shared across every entry in a single [translate] call, so the value budget is
   * enforced across the whole update. Once the budget is spent, the overflowing entry and every
   * older entry are dropped, while newer (already-processed) entries are preserved.
   */
  internal class ArgumentSanitizer(
    private val logger: ILogger,
    private val warningState: WarningState,
  ) {

    private val activeContainers = IdentityHashMap<Any, Unit>()
    private var remainingValues = MAX_ARGUMENT_VALUES
    private var budgetExhausted = false

    /**
     * Sanitizes one entry's arguments, or returns an empty map to drop them, either because the
     * structure is cyclic or too deeply nested (this entry only), or because the shared per-update
     * value budget is spent (this entry and every older one).
     */
    @Suppress("TooGenericExceptionCaught")
    fun sanitizeEntry(raw: Map<String, Any?>): Map<String, Any?> {
      if (budgetExhausted) {
        return emptyMap()
      }

      return try {
        sanitizeMap(raw, depth = 0)
      } catch (drop: DropSubtree) {
        if (drop.exhaustsBudget) {
          budgetExhausted = true
        }
        logger.log(WARNING, drop.warning)
        emptyMap()
      } catch (t: Throwable) {
        // Extracted maps may invoke host app code while iterating or stringifying values.
        ExceptionUtils.rethrowIfFatal(t)
        logger.log(WARNING, STRUCTURE_WARNING, t)
        emptyMap()
      }
    }

    private fun sanitizeMap(value: Map<*, *>, depth: Int): Map<String, Any?> {
      enter(value)
      try {
        val sanitized = LinkedHashMap<String, Any?>()
        for ((key, childValue) in value) {
          sanitized[key.toString()] = sanitizeValue(childValue, depth + 1)
        }
        return sanitized
      } finally {
        exit(value)
      }
    }

    private fun sanitizeCollection(value: Collection<*>, depth: Int): List<Any?> {
      enter(value)
      try {
        // The value budget bounds allocation instead of the caller-provided collection size.
        val sanitized = ArrayList<Any?>()
        for (childValue in value) {
          sanitized += sanitizeValue(childValue, depth + 1)
        }
        return sanitized
      } finally {
        exit(value)
      }
    }

    private fun sanitizeValue(value: Any?, depth: Int): Any? {
      visit(depth)
      val collection = value?.asSanitizableCollectionOrNull()

      return when {
        value == null || value is String || value is Number || value is Boolean -> value
        value is CharSequence || value is Char -> value.toString()
        value is Enum<*> -> value.name
        value is Map<*, *> -> sanitizeMap(value, depth)
        collection != null -> sanitizeCollection(collection, depth)
        else -> {
          warningState.logUnsupportedValueWarning(value::class.simpleName, logger)
          value.toString()
        }
      }
    }

    private fun Any.asSanitizableCollectionOrNull(): Collection<*>? =
      when (this) {
        is Collection<*> -> this
        is Array<*> -> asList()
        is BooleanArray -> asList()
        is ByteArray -> asList()
        is ShortArray -> asList()
        is IntArray -> asList()
        is LongArray -> asList()
        is FloatArray -> asList()
        is DoubleArray -> asList()
        is CharArray -> asList()
        else -> null
      }

    /**
     * Records a visit to one value, enforcing the per-entry depth cap and the shared per-update
     * value budget. Throws [DropSubtree] to abort the current subtree when either is exceeded.
     */
    private fun visit(depth: Int) {
      if (depth > MAX_ARGUMENT_DEPTH) {
        throw DropSubtree(STRUCTURE_WARNING, exhaustsBudget = false)
      }
      if (--remainingValues < 0) {
        throw DropSubtree(BUDGET_WARNING, exhaustsBudget = true)
      }
    }

    private fun enter(container: Any) {
      if (activeContainers.put(container, Unit) != null) {
        throw DropSubtree(STRUCTURE_WARNING, exhaustsBudget = false)
      }
    }

    private fun exit(container: Any) {
      activeContainers.remove(container)
    }

    /**
     * Control-flow signal to abort sanitization of the current subtree. Internal to
     * [ArgumentSanitizer].
     *
     * [exhaustsBudget] distinguishes an entry-local drop (cycle or over-deep structure) from an
     * update-wide one (the shared value budget is spent). Overrides [fillInStackTrace] to skip
     * stack-trace capture.
     */
    private class DropSubtree(val warning: String, val exhaustsBudget: Boolean) :
      RuntimeException() {
      override fun fillInStackTrace(): Throwable = this
    }

    private companion object {

      /**
       * Max nesting depth allowed while sanitizing a single argument value for a given back stack
       * entry.
       *
       * If exceeded, all arguments for that back stack entry are dropped.
       */
      private const val MAX_ARGUMENT_DEPTH = 20

      /**
       * Max number of argument values visited while sanitizing all entries in a given back stack
       * update.
       *
       * If exceeded, the entry that overflows loses its arguments, as do older entries; newer
       * entries are preserved. E.g., suppose we have the following back stack:
       * - /Checkout -> Top of the stack and processed first
       * - /ProductDetail -> Processed second and overflows the `MAX_ARGUMENT_VALUES` budget
       * - /Home
       *
       * Then /ProductDetail and /Home will have no arguments, but /Checkout will.
       */
      private const val MAX_ARGUMENT_VALUES = 1_000

      private const val STRUCTURE_WARNING =
        "Nav3 argument sanitization failed (possibly a cyclic or deeply nested structure). " +
          "Skipping arguments."

      private const val BUDGET_WARNING =
        "Nav3 arguments exceeded the maximum total value count for one backstack update. " +
          "Skipping arguments for this and older captured entries."
    }
  }

  /** A small state wrapper that lets us avoid spamming logs when sanitizing arguments. */
  internal class WarningState {
    private var hasLoggedUnsupportedValueWarning = false
    private var hasLoggedInvalidRouteNameWarning = false
    private var hasLoggedNameExtractorFailureWarning = false

    fun logUnsupportedValueWarning(typeName: String?, logger: ILogger) {
      if (hasLoggedUnsupportedValueWarning) {
        return
      }

      logger.log(
        WARNING,
        "Nav3 argumentsExtractor returned unsupported value of type %s while processing this back " +
          "stack update. Falling back to toString(). Use String, CharSequence, Char, Number, " +
          "Boolean, Enum, Map, Collection, object Array, and primitive array values for reliable " +
          "results.",
        typeName,
      )
      hasLoggedUnsupportedValueWarning = true
    }

    fun logInvalidRouteNameWarning(logger: ILogger) {
      if (hasLoggedInvalidRouteNameWarning) {
        return
      }

      logger.log(
        WARNING,
        "Nav3 nameExtractor returned a blank route name while processing this back stack update. " +
          "Using /unknown instead.",
      )
      hasLoggedInvalidRouteNameWarning = true
    }

    fun logNameExtractorFailureWarning(logger: ILogger, throwable: Throwable) {
      if (hasLoggedNameExtractorFailureWarning) {
        return
      }

      logger.log(
        WARNING,
        "Nav3 nameExtractor threw while resolving a route name. Using /unknown instead.",
        throwable,
      )
      hasLoggedNameExtractorFailureWarning = true
    }
  }
}

/**
 * Summary information about a back stack entry from the host app, fit for use with Sentry data.
 *
 * All route names should be normalized to include a leading slash, and all arguments should be
 * sanitized (i.e., bounded in size and depth, and converted into a serializable form).
 */
internal data class Route(
  val name: String,
  val arguments: Map<String, Any?> = emptyMap(),
) {

  /**
   * Returns this route in serialized form. E.g.:
   * ```
   *  {
   *    "route": "/ProductScreen"
   *    "args": {
   *      "product_id": 12345
   *      "promo_id:": "spring-marketing-drive-2026"
   *    }
   *  }
   * ```
   */
  fun serialize(): Map<String, Any?> = buildMap {
    put("route", name)
    if (arguments.isNotEmpty()) {
      put("args", arguments)
    }
  }
}

internal fun List<Route>.serialize(): List<Map<String, Any?>> = map(Route::serialize)
