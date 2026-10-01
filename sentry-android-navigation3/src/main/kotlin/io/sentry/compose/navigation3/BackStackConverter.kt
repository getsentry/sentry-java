package io.sentry.compose.navigation3

import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME
import io.sentry.util.ExceptionUtils
import java.util.IdentityHashMap

/**
 * Converts the host app back stack into a list of [NormalizedSentryBackStackEntry]s.
 *
 * **Exception handling**
 *
 * Invocations of the host-provided [BackStackEntryMapper] and sanitization of host-defined
 * arguments are protected by broad `try-catch` clauses, as each may throw arbitrary exceptions.
 *
 * We avoid failing fast on the assumption that nav telemetry is supplemental, and falling back to
 * an [UNKNOWN_ENTRY_NAME] or losing an argument map is preferable to crashing.
 *
 * **Threading policy**
 *
 * This class performs work synchronously on the calling thread. The host app's
 * [BackStackEntryMapper] is invoked on that same thread and should remain small, non-blocking, and
 * safe for the caller's threading context.
 */
internal class BackStackConverter<T : Any>(
  private val entryMapper: ForwardingBackStackEntryMapper<T>,
  private val logger: ILogger,
) {

  /**
   * Converts the provided [backStack] into a list of [NormalizedSentryBackStackEntry]s by invoking
   * the host app-provided [entryMapper] and normalizing the results.
   *
   * The returned list has the same order as `backStack`.
   */
  fun convert(
    backStack: List<T>,
    retentionPolicy: RetentionPolicy,
  ): List<NormalizedSentryBackStackEntry> {
    val warningState = WarningState()
    val sanitizer = ArgumentSanitizer(logger, warningState)

    val entries = MutableList<NormalizedSentryBackStackEntry?>(backStack.size) { null }
    val indicesInPolicyOrder =
      when (retentionPolicy) {
        RetentionPolicy.KEEP_FIRST -> backStack.indices
        RetentionPolicy.KEEP_LAST -> backStack.indices.reversed()
      }

    for (index in indicesInPolicyOrder) {
      val entry = backStack[index]
      entries[index] = normalize(entry, sanitizer, warningState)
    }

    return entries.requireNoNulls()
  }

  @Suppress("TooGenericExceptionCaught")
  private fun normalize(
    backStackEntry: T,
    sanitizer: ArgumentSanitizer,
    warningState: WarningState,
  ): NormalizedSentryBackStackEntry {
    val info =
      try {
        entryMapper.map(backStackEntry)
      } catch (t: Throwable) {
        // Back stack entry mappers are host app callbacks.
        ExceptionUtils.rethrowIfFatal(t)
        warningState.logMapperFailureWarning(logger, t)
        return NormalizedSentryBackStackEntry(UNKNOWN_ENTRY_NAME)
      }

    if (info == null) {
      return NormalizedSentryBackStackEntry(UNKNOWN_ENTRY_NAME)
    }

    val arguments = info.arguments?.let(sanitizer::sanitizeEntry) ?: emptyMap()
    val formattedName = NormalizedSentryBackStackEntry.formatName(info.name)

    return if (formattedName.isBlank()) {
      warningState.logInvalidNameWarning(logger)
      NormalizedSentryBackStackEntry(UNKNOWN_ENTRY_NAME, arguments)
    } else {
      NormalizedSentryBackStackEntry(formattedName, arguments)
    }
  }

  /**
   * Specifies whether entry info starting at the initial or final element of a back stack list
   * should be preserved if a size budget is exceeded.
   *
   * Most clients will want to select the policy that starts at the top of their back stack.
   */
  internal enum class RetentionPolicy {

    /**
     * Retains entry info for lower indexed elements in the back stack list if a particular info
     * budget is reached. Retention starts at index 0 and increments until the budget is exhausted.
     */
    KEEP_FIRST,

    /**
     * Retains entry info for higher indexed elements in the back stack list if a particular info
     * budget is reached. Retention starts at lastIndex and decrements until the budget is
     * exhausted.
     */
    KEEP_LAST,
  }

  /**
   * Sanitizes a back stack entry's arguments and writes them in a serializable form. It bounds
   * depth and total value count, and it rejects cyclic structures.
   *
   * One instance is shared across every entry in a single [convert] call, so the value budget is
   * enforced across the whole update. Once the budget is spent, the overflowing entry and every
   * older entry are dropped, while newer (already-processed) entries are preserved.
   */
  internal class ArgumentSanitizer(
    private val logger: ILogger,
    private val warningState: WarningState,
  ) {

    private val activeContainers = IdentityHashMap<Any, Unit>()
    private var remainingValues = MAX_ARGUMENT_COUNT
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
        val sanitized = mutableMapOf<String, Any?>()
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
        val sanitized = mutableListOf<Any?>()
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
       * For instance, `mapOf("id" to 123)` has a depth of 1; `mapOf("items" to listOf("apple",
       * "banana"))` has a depth of 2.
       *
       * If exceeded, all arguments for that back stack entry are dropped.
       */
      private const val MAX_ARGUMENT_DEPTH = 10

      /**
       * Max number of argument values visited while sanitizing all entries in a given back stack
       * update.
       *
       * For instance, `mapOf("id" to 123)` consumes 1 value; `mapOf("profile" to mapOf("id" to 123,
       * "name" to "Ada"))` consumes 3 values.
       *
       * If exceeded, the entry that overflows loses its arguments, as do older entries; newer
       * entries are preserved.
       *
       * For instance, suppose we have the following back stack:
       *
       * - /Checkout -> Top of the stack and processed first
       * - /ProductDetail -> Processed second and overflows the `MAX_ARGUMENT_VALUES` budget
       * - /Home
       *
       * Then /ProductDetail and /Home will have no arguments, but /Checkout will.
       */
      private const val MAX_ARGUMENT_COUNT = 500

      private const val BUDGET_WARNING =
        "Nav3 arguments exceeded the maximum total value count for one backstack update. Skipping arguments " +
          "for this and older captured entries."

      private const val STRUCTURE_WARNING =
        "Nav3 argument sanitization failed (possibly a cyclic or deeply nested structure). Skipping arguments."
    }
  }

  /** A small state wrapper that lets us avoid spamming logs when sanitizing arguments. */
  internal class WarningState {

    private var hasLoggedInvalidNameWarning = false
    private var hasLoggedMapperFailureWarning = false
    private var hasLoggedUnsupportedValueWarning = false

    fun logInvalidNameWarning(logger: ILogger) {
      if (hasLoggedInvalidNameWarning) {
        return
      }

      logger.log(
        WARNING,
        "Nav3 backStackEntryMapper returned a blank name while processing this back stack update. " +
          "Using $UNKNOWN_ENTRY_NAME instead.",
      )
      hasLoggedInvalidNameWarning = true
    }

    fun logMapperFailureWarning(logger: ILogger, throwable: Throwable) {
      if (hasLoggedMapperFailureWarning) {
        return
      }

      logger.log(
        WARNING,
        "Nav3 backStackEntryMapper threw while resolving an entry. " +
          "Using $UNKNOWN_ENTRY_NAME without arguments instead.",
        throwable,
      )
      hasLoggedMapperFailureWarning = true
    }

    fun logUnsupportedValueWarning(typeName: String?, logger: ILogger) {
      if (hasLoggedUnsupportedValueWarning) {
        return
      }

      logger.log(
        WARNING,
        "Nav3 backStackEntryMapper returned unsupported argument value of type %s while processing this back " +
          "stack update. Falling back to toString(). Use String, CharSequence, Char, Number, " +
          "Boolean, Enum, Map, Collection, object Array, and primitive array values for reliable " +
          "results.",
        typeName,
      )
      hasLoggedUnsupportedValueWarning = true
    }
  }
}

/** A normalized version of a [SentryBackStackEntry] produced by a [BackStackEntryMapper]. */
internal data class NormalizedSentryBackStackEntry(
  /** A [SentryBackStackEntry.name] trimmed and formatted to include a leading "/". */
  val name: String,
  /** Sanitized [SentryBackStackEntry.arguments] (i.e., bounded in size and depth). */
  val arguments: Map<String, Any?> = emptyMap(),
) {

  companion object {

    const val UNKNOWN_ENTRY_NAME = "/unknown"

    /**
     * Trims [name] and adds a "/" prefix if one isn't already present.
     *
     * Matches the Nav2, Dart, and Web naming patterns.
     */
    fun formatName(name: String): String {
      val trimmedName = name.trim()
      return when {
        trimmedName.isEmpty() -> ""
        trimmedName.startsWith("/") -> trimmedName
        else -> "/$trimmedName"
      }
    }
  }

  /**
   * Returns this entry in serialized form. E.g.:
   * ```
   *  {
   *    "entry": "/ProductScreen"
   *    "arguments": {
   *      "product_id": 12345
   *      "promo_id:": "spring-marketing-drive-2026"
   *    }
   *  }
   * ```
   */
  fun serialize(): Map<String, Any?> = buildMap {
    put("entry", name)
    if (arguments.isNotEmpty()) {
      put("arguments", arguments)
    }
  }
}

internal fun List<NormalizedSentryBackStackEntry>.serialize(): List<Map<String, Any?>> =
  map(NormalizedSentryBackStackEntry::serialize)
