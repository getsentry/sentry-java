package io.sentry.compose.navigation3.internal.backstack

import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.internal.backstack.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME

/**
 * Why a back stack entry was recorded as [UNKNOWN_ENTRY_NAME], recorded next to it under
 * [UNKNOWN_ENTRY_REASON_KEY] (prefixed with `to_` or `from_` on breadcrumbs) so that the reason
 * reaches Sentry without debug logs.
 *
 * Values contain no app data or class names.
 */
internal enum class UnknownEntryReason(val serializedValue: String) {

  /** The mapper returned a name that's empty after trimming. The entry's arguments are kept. */
  BLANK_NAME("blank_name"),

  /** A built-in mapper can't name the entry, as its `@Serializable` class has type parameters. */
  GENERIC_TYPE("generic_type"),

  /**
   * The built-in mappers couldn't be created, as the app's kotlinx.serialization version can't be
   * linked or failed their compatibility check.
   */
  KOTLINX_SERIALIZATION_VERSION("kotlinx_serialization_version"),

  /** The mapper threw. */
  MAPPING_FAILED("mapping_failed"),

  /** A built-in mapper can't name the entry, as its class isn't annotated with `@Serializable`. */
  NOT_SERIALIZABLE("not_serializable"),

  /**
   * A built-in mapper can't name the entry, as reading its class's annotations, serializer, or
   * descriptor threw.
   */
  SERIALIZER_LOOKUP_THREW("serializer_lookup_threw"),

  /**
   * A built-in mapper can't name the entry, as looking up its `@Serializable` class's serializer
   * found none, e.g., because R8 removed what the lookup needs.
   */
  SERIALIZER_NOT_FOUND("serializer_not_found"),

  /**
   * The mapper returned `null` without a built-in mapper reporting why, i.e., a custom mapper chose
   * not to name the entry.
   */
  UNMAPPED("unmapped");

  companion object {
    const val UNKNOWN_ENTRY_REASON_KEY: String = "unknown_entry_reason"
  }
}

/**
 * Passes the [UnknownEntryReason] a built-in [BackStackEntryMapper] knows to the back stack
 * converter, as the mapper reports a failure by returning `null`, which is public API.
 *
 * A built-in mapper [report]s the reason right before returning `null`, and the converter
 * [collect]s it around each call to the app's mapper. So a custom mapper that forwards a built-in
 * mapper's `null` forwards its reason too, while one that returns `null` itself reports nothing,
 * and one that returns a name has its reason ignored. If a custom mapper calls built-in mappers
 * several times for one entry, the last reason reported is kept.
 *
 * Kept per thread, as mappers run on the converter's thread.
 */
internal object UnknownEntryReasonReports {

  private val reported = ThreadLocal<UnknownEntryReason?>()

  /** Reports why the built-in mapper that's about to return `null` can't name its entry. */
  fun report(reason: UnknownEntryReason) {
    reported.set(reason)
  }

  /**
   * Runs [map] and returns its result with the last reason reported while it ran, or `null` if none
   * was. Clears the report before and after, even if [map] throws, so a reason can't carry over to
   * another entry.
   */
  fun <R> collect(map: () -> R): Pair<R, UnknownEntryReason?> {
    reported.remove()
    try {
      val result = map()
      return result to reported.get()
    } finally {
      reported.remove()
    }
  }
}
