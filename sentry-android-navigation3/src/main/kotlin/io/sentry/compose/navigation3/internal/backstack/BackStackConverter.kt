package io.sentry.compose.navigation3.internal.backstack

import androidx.compose.runtime.snapshots.Snapshot
import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.contract.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.contract.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.contract.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.compose.navigation3.internal.contract.ArgumentDropReason
import io.sentry.compose.navigation3.internal.contract.ArgumentDropReason.Companion.ARGUMENT_DROP_REASON_KEY
import io.sentry.compose.navigation3.internal.contract.BACK_STACK_ENTRY_ARGUMENTS_KEY
import io.sentry.compose.navigation3.internal.contract.BACK_STACK_ENTRY_KEY
import io.sentry.compose.navigation3.internal.contract.TRANSACTION_ARGUMENTS_KEY
import io.sentry.compose.navigation3.internal.contract.UNKNOWN_ENTRY_NAME
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason.Companion.UNKNOWN_ENTRY_REASON_KEY
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
 * `BackStackEntryMapper` is invoked on that same thread and should remain small, non-blocking, and
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
        return NormalizedSentryBackStackEntry(
          name = UNKNOWN_ENTRY_NAME,
          unknownEntryReason = UnknownEntryReason.MAPPING_FAILED,
        )
      }

    if (info == null) {
      return NormalizedSentryBackStackEntry(
        name = UNKNOWN_ENTRY_NAME,
        unknownEntryReason = UnknownEntryReason.UNMAPPED,
      )
    }

    val sanitizedArguments =
      info.arguments?.let { arguments ->
        // Argument conversion may read Compose state through host app code. Suppress read
        // observation so changes to that state don't trigger recomposition solely b/c our
        // telemetry code read it.
        Snapshot.withoutReadObservation { sanitizer.sanitizeEntry(arguments) }
      } ?: SanitizedArguments(emptyMap())
    val formattedName = NormalizedSentryBackStackEntry.formatName(info.name)

    return if (formattedName.isBlank()) {
      warningState.logInvalidNameWarning(logger)
      NormalizedSentryBackStackEntry(
        name = UNKNOWN_ENTRY_NAME,
        arguments = sanitizedArguments.values,
        argumentDropReason = sanitizedArguments.dropReason,
        unknownEntryReason = UnknownEntryReason.BLANK_NAME,
      )
    } else {
      NormalizedSentryBackStackEntry(
        name = formattedName,
        arguments = sanitizedArguments.values,
        argumentDropReason = sanitizedArguments.dropReason,
      )
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

  internal data class SanitizedArguments(
    val values: Map<String, Any?>,
    val dropReason: ArgumentDropReason? = null,
  )

  /**
   * Sanitizes the arguments the [entryMapper] returns for back stack entries and returns them in a
   * [serializable form][SanitizedArguments.values]. It bounds depth and total value count, and it
   * rejects cyclic structures.
   *
   * One instance is shared across every entry in a single [convert] call, so the value and
   * character budgets span the whole update.
   */
  internal class ArgumentSanitizer(
    private val logger: ILogger,
    private val warningState: WarningState,
  ) {

    private var remainingValues = MAX_ARGUMENT_VALUES
    private var remainingCharacters = MAX_ARGUMENT_CHARACTERS
    private var dropReason: ArgumentDropReason? = null

    /** The maps and lists currently being sanitized, so that one containing itself is rejected. */
    private val activeContainers = IdentityHashMap<Any, Unit>()

    /**
     * Sanitizes one entry's raw argument inputs and returns the result.
     *
     * [SanitizedArguments.values] will be empty if [raw] is empty or if the arguments were dropped.
     * Arguments can be dropped if:
     *
     * - a previously processed entry exceeded the shared value or character budget;
     * - an argument constraint is violated; or
     * - conversion fails.
     *
     * [SanitizedArguments.dropReason] will be non-null if and only if arguments are dropped.
     */
    @Suppress("TooGenericExceptionCaught")
    fun sanitizeEntry(raw: Map<String, Any?>): SanitizedArguments {
      dropReason?.let { reason ->
        return SanitizedArguments(emptyMap(), reason)
      }

      return try {
        SanitizedArguments(sanitizeMap(raw, depth = 0))
      } catch (drop: DropArguments) {
        if (drop.reason.exhaustsUpdateBudget) {
          dropReason = drop.reason
        }
        logger.log(WARNING, drop.warning)
        SanitizedArguments(emptyMap(), drop.reason)
      } catch (t: Throwable) {
        // Extracted maps may invoke host app code while iterating or stringifying values.
        ExceptionUtils.rethrowIfFatal(t)
        logger.log(WARNING, STRUCTURE_WARNING, t)
        SanitizedArguments(emptyMap(), ArgumentDropReason.SANITIZATION_FAILED)
      } finally {
        // A drop or failure leaves the containers it was inside of open.
        activeContainers.clear()
      }
    }

    /** Sanitizes [map], whose values are at [depth] + 1. The entry's root map has depth 0. */
    private fun sanitizeMap(map: Map<*, *>, depth: Int): Map<String, Any?> {
      enter(map)
      val sanitized = LinkedHashMap<String, Any?>()
      for ((key, value) in map) {
        val name = spendText(key.toString())
        if (sanitized.containsKey(name)) {
          throw duplicateKey(map, key, name)
        }
        sanitized[name] = sanitizeValue(value, depth + 1)
      }
      activeContainers.remove(map)
      return sanitized
    }

    private fun sanitizeValue(value: Any?, depth: Int): Any? {
      // A value at depth n is nested in n containers, counting the entry's root map.
      if (depth > MAX_ARGUMENT_DEPTH) {
        throw DropArguments(ArgumentDropReason.INVALID_STRUCTURE, STRUCTURE_WARNING)
      }
      if (--remainingValues < 0) {
        throw DropArguments(ArgumentDropReason.MAX_COUNT, MAX_COUNT_WARNING)
      }

      if (value is Map<*, *>) {
        return sanitizeMap(value, depth)
      }
      val collection = value?.asCollectionOrNull() ?: return sanitizeScalar(value)

      enter(value)
      // Iterated without reading the collection's size, which the host app controls.
      val sanitized = ArrayList<Any?>()
      for (element in collection) {
        sanitized += sanitizeValue(element, depth + 1)
      }
      activeContainers.remove(value)
      return sanitized
    }

    private fun sanitizeScalar(value: Any?): Any? =
      when (value) {
        null,
        is Boolean -> value
        // JSON has no NaN or infinity, and Sentry's JSON writer throws for them, which would drop
        // the whole event these arguments are attached to. We record them as strings instead.
        is Number -> if (value.isNonFinite()) spendText(value.toString()) else value
        is CharSequence -> spendText(value)
        is Char -> spendText(value.toString())
        is Enum<*> -> spendText(value.name)
        else -> {
          warningState.logUnsupportedValueWarning(value::class.simpleName, logger)
          spendText(value.toString())
        }
      }

    /**
     * Returns [text] as a [String] after spending its characters.
     *
     * Checks [text]'s length before converting it, so that host app text that doesn't fit isn't
     * copied, and again after, in case a custom [CharSequence]'s length doesn't match its text.
     */
    private fun spendText(text: CharSequence): String {
      checkFits(text.length)
      val string = text.toString()
      checkFits(string.length)
      remainingCharacters -= string.length
      return string
    }

    private fun checkFits(characters: Int) {
      if (characters > remainingCharacters) {
        throw DropArguments(ArgumentDropReason.CHARACTER_LIMIT, MAX_CHARACTER_WARNING)
      }
    }

    private fun enter(container: Any) {
      if (activeContainers.put(container, Unit) != null) {
        throw DropArguments(ArgumentDropReason.INVALID_STRUCTURE, STRUCTURE_WARNING)
      }
    }

    /**
     * Names only the colliding keys' types, as the keys themselves may contain host data.
     * Collisions are rare, so we re-scan [map] instead of tracking the original keys of every map.
     */
    private fun duplicateKey(map: Map<*, *>, key: Any?, name: String): DropArguments {
      val otherKey = map.keys.firstOrNull { it !== key && it.toString() == name }
      return DropArguments(
        ArgumentDropReason.DUPLICATE_KEY,
        "A Nav3 map key (${key?.let { it::class.simpleName }}) stringifies to the same string " +
          "as another key (${otherKey?.let { it::class.simpleName }}). Dropping this entry's " +
          "arguments.",
      )
    }

    /**
     * Control-flow signal to drop an entry's arguments, logging [warning]. Overrides
     * [fillInStackTrace] to skip stack-trace capture.
     */
    private class DropArguments(val reason: ArgumentDropReason, val warning: String) :
      RuntimeException() {
      override fun fillInStackTrace(): Throwable = this
    }

    companion object {

      const val MAX_CHARACTER_WARNING =
        "Nav3 back stack update exceeded the argument character limit. Dropping arguments for " +
          "this and older entries."

      const val MAX_COUNT_WARNING =
        "Nav3 back stack update exceeded the argument value limit. Dropping arguments for this " +
          "and older entries."

      const val STRUCTURE_WARNING =
        "Nav3 argument sanitization failed (possibly cyclic or too deeply nested). Dropping this " +
          "entry's arguments."
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
        "Nav3 backStackEntryMapper returned a blank name. Using $UNKNOWN_ENTRY_NAME.",
      )
      hasLoggedInvalidNameWarning = true
    }

    fun logMapperFailureWarning(logger: ILogger, throwable: Throwable) {
      if (hasLoggedMapperFailureWarning) {
        return
      }

      logger.log(
        WARNING,
        "Nav3 backStackEntryMapper threw. Using $UNKNOWN_ENTRY_NAME without arguments.",
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
        "Nav3 argument type %s is unsupported. Using toString(). Return scalars, enums, maps, " +
          "collections, or arrays from BackStackEntryMapper.",
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
  /**
   * The reason why the SDK dropped host-provided arguments, or null if no arguments were dropped.
   */
  val argumentDropReason: ArgumentDropReason? = null,
  /** Why the entry was recorded as [UNKNOWN_ENTRY_NAME], or `null` if it has a name. */
  val unknownEntryReason: UnknownEntryReason? = null,
) {

  companion object {

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

  /** Returns sanitized host arguments together with SDK-owned argument metadata. */
  fun argumentsWithMetadata(): Map<String, Any?> {
    val reason = argumentDropReason ?: return arguments
    return buildMap {
      putAll(arguments)
      put(ARGUMENT_DROP_REASON_KEY, reason.serializedValue)
    }
  }

  /**
   * Returns this entry in serialized form. E.g.:
   * ```
   *  {
   *    "entry": "/ProductScreen"
   *    "entry_arguments": {
   *      "product_id": 12345
   *      "promo_id:": "spring-marketing-drive-2026"
   *    }
   *  }
   * ```
   *
   * An unnamed entry also has its [unknownEntryReason], e.g.:
   * ```
   *  {
   *    "entry": "/unknown"
   *    "unknown_entry_reason": "unmapped"
   *  }
   * ```
   */
  fun serialize(): Map<String, Any?> = buildMap {
    put(BACK_STACK_ENTRY_KEY, name)
    unknownEntryReason?.let { put(UNKNOWN_ENTRY_REASON_KEY, it.serializedValue) }
    argumentsWithMetadata()
      .takeIf { it.isNotEmpty() }
      ?.let { put(BACK_STACK_ENTRY_ARGUMENTS_KEY, it) }
  }
}

/** Returns the data to set on the nav transaction of a destination this entry is the top of. */
internal fun NormalizedSentryBackStackEntry.transactionData(): Map<String, Any> = buildMap {
  argumentsWithMetadata().takeIf { it.isNotEmpty() }?.let { put(TRANSACTION_ARGUMENTS_KEY, it) }
  unknownEntryReason?.let { put(UNKNOWN_ENTRY_REASON_KEY, it.serializedValue) }
}

internal fun List<NormalizedSentryBackStackEntry>.serialize(): List<Map<String, Any?>> =
  map(NormalizedSentryBackStackEntry::serialize)

/** Returns this collection, or this array as a list, or `null` if it's neither. */
private fun Any.asCollectionOrNull(): Collection<*>? =
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

/** Whether this is a NaN or infinite `Double` or `Float`. */
private fun Number.isNonFinite(): Boolean =
  (this is Double && !isFinite()) || (this is Float && !isFinite())
