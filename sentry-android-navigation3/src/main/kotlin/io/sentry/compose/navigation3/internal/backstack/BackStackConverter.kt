package io.sentry.compose.navigation3.internal.backstack

import androidx.compose.runtime.snapshots.Snapshot
import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason.Companion.ARGUMENT_DROP_REASON_KEY
import io.sentry.compose.navigation3.internal.backstack.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME
import io.sentry.util.ExceptionUtils

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
          argumentDropReason = ArgumentDropReason.MAPPING_FAILED,
        )
      }

    if (info == null) {
      return NormalizedSentryBackStackEntry(name = UNKNOWN_ENTRY_NAME)
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
   * Sanitizes a back stack entry's raw argument inputs and returns them in a
   * [serializable form][SanitizedArguments.values].
   *
   * Internally, this class processes argument inputs returned by the [entryMapper] passed to the
   * outer converter instance. Those inputs can come from built-in or custom
   * [BackStackEntryMapper]s.
   *
   * Built-in mappers supply their own [ArgumentSource], while custom mappers typically supply an
   * ordinary map. [HostArgumentSource] adapts ordinary maps to the [ArgumentSource] contract, so
   * both paths can be handled by the same processing pipeline.
   */
  internal class ArgumentSanitizer(
    private val logger: ILogger,
    private val warningState: WarningState,
  ) {

    // One instance per sanitizer, so the budgets span every entry of a back stack update.
    private val budget = ArgumentBudget()
    private var dropReason: ArgumentDropReason? = null

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

      // Use the raw inputs' own source implementation when available; otherwise, provide them with
      // an adapter.
      val source =
        raw as? ArgumentSource
          ?: HostArgumentSource(raw) { warningState.logUnsupportedValueWarning(it, logger) }
      val sink = TreeArgumentWriter(budget, rootIdentity = raw)

      // A constraint violation the sink recorded takes precedence over whatever the source threw,
      // as for ArgumentSink.throwIfFailed. Here it's the reason the arguments are dropped with.
      return try {
        source.writeTo(sink)
        SanitizedArguments(sink.argumentsOrThrow())
      } catch (drop: DropArguments) {
        drop(sink.failure ?: drop)
      } catch (t: Throwable) {
        // Extracted maps may invoke host app code while iterating or stringifying values.
        ExceptionUtils.rethrowIfFatal(t)
        sink.failure?.let {
          return drop(it)
        }
        logger.log(WARNING, DropArguments.STRUCTURE_WARNING, t)
        SanitizedArguments(emptyMap(), ArgumentDropReason.SANITIZATION_FAILED)
      }
    }

    private fun drop(drop: DropArguments): SanitizedArguments {
      if (drop.reason.exhaustsUpdateBudget) {
        dropReason = drop.reason
      }
      drop.warning?.let { logger.log(WARNING, it) }
      return SanitizedArguments(emptyMap(), drop.reason)
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
   */
  fun serialize(): Map<String, Any?> = buildMap {
    put("entry", name)
    argumentsWithMetadata().takeIf { it.isNotEmpty() }?.let { put("entry_arguments", it) }
  }
}

internal fun List<NormalizedSentryBackStackEntry>.serialize(): List<Map<String, Any?>> =
  map(NormalizedSentryBackStackEntry::serialize)

/** Why host-provided arguments were unavailable in emitted nav state. */
internal enum class ArgumentDropReason(
  val serializedValue: String,
  val exhaustsUpdateBudget: Boolean = false,
) {

  CHARACTER_LIMIT("max_character_limit_exceeded", exhaustsUpdateBudget = true),
  DUPLICATE_KEY("duplicate_key"),
  INVALID_STRUCTURE("invalid_structure"),
  MAPPING_FAILED("mapping_failed"),
  MAX_COUNT("max_argument_count_exceeded", exhaustsUpdateBudget = true),
  SANITIZATION_FAILED("sanitization_failed");

  companion object {
    const val ARGUMENT_DROP_REASON_KEY = "dropped_by_sentry"
  }
}
