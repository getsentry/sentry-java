package io.sentry.compose.navigation3.internal.contract

/*
 * Internal properties that are part of our public contract because they're displayed in
 * the Sentry UI or define observable behavior.
 */

// The context containing the captured navigation back stack.
internal const val NAVIGATION_CONTEXT_KEY = "Navigation"
internal const val BACK_STACK_KEY = "Back Stack"

// Fields in a serialized back stack entry.
internal const val BACK_STACK_ENTRY_KEY = "entry"
internal const val BACK_STACK_ENTRY_ARGUMENTS_KEY = "entry_arguments"

// The transaction data field containing the destination's arguments.
internal const val TRANSACTION_ARGUMENTS_KEY = "arguments"

// Fields in a navigation breadcrumb.
internal const val BREADCRUMB_FROM_KEY = "from"
internal const val BREADCRUMB_TO_KEY = "to"
internal const val BREADCRUMB_FROM_ARGUMENTS_KEY = "from_arguments"
internal const val BREADCRUMB_TO_ARGUMENTS_KEY = "to_arguments"
internal const val BREADCRUMB_FROM_UNKNOWN_ENTRY_REASON_KEY = "from_unknown_entry_reason"
internal const val BREADCRUMB_TO_UNKNOWN_ENTRY_REASON_KEY = "to_unknown_entry_reason"

internal const val NAVIGATION_OP = "navigation"
internal const val NAVIGATION_TRANSACTION_ORIGIN = "auto.navigation.nav3"

/** The string displayed in the Sentry UI if the name of a back stack entry can't be determined. */
internal const val UNKNOWN_ENTRY_NAME = "/unknown"

/**
 * Why a back stack entry was reported as [UNKNOWN_ENTRY_NAME].
 *
 * Recorded under [UNKNOWN_ENTRY_REASON_KEY] (prefixed with `to_` or `from_` on breadcrumbs).
 */
internal enum class UnknownEntryReason(val serializedValue: String) {

  // General reasons

  /** The mapper returned a name that was empty after trimming. */
  BLANK_NAME("blank_name"),

  /** The mapper threw. */
  MAPPING_FAILED("mapping_failed"),

  /**
   * The mapper returned `null` without a built-in mapper reporting why, i.e., a custom mapper chose
   * not to name the entry.
   */
  UNMAPPED("unmapped"),

  // Reasons specific to built-in mappers

  /**
   * A built-in mapper couldn't name the entry because the entry's class wasn't annotated with
   * `@Serializable`.
   */
  ENTRY_NOT_ANNOTATED("entry_not_annotated"),

  /**
   * A built-in mapper couldn't name the entry because the entry's `@Serializable` class had one or
   * more type parameters (of any sort).
   *
   * Looking up a generic type's serializer requires serializers for its type arguments, which the
   * mapper can't recover from the entry at runtime.
   *
   * Detected from the entry class's generic signature. If R8 removes that signature, the entry will
   * be assigned [SERIALIZER_NOT_FOUND] instead.
   */
  GENERIC_ENTRY_TYPE("generic_entry_type"),

  /**
   * A built-in mapper couldn't be created because the host app uses a missing or incompatible
   * version of `kotlinx-serialization-core`.
   */
  KOTLINX_SERIALIZATION_VERSION("kotlinx_serialization_version"),

  /**
   * A built-in mapper couldn't name the entry because an exception was thrown while trying to read
   * the entry's class's annotations, serializer, or descriptor.
   */
  SERIALIZER_ACCESS_THREW("serializer_access_threw"),

  /**
   * A built-in mapper can't name the entry because it couldn't find the entry class's serializer,
   * e.g., because R8 removed what the lookup needs.
   */
  SERIALIZER_NOT_FOUND("serializer_not_found");

  companion object {

    const val UNKNOWN_ENTRY_REASON_KEY: String = "unknown_entry_reason"
  }
}

/**
 * Why host-provided arguments were unavailable in emitted nav state.
 *
 * Recorded under [ARGUMENT_DROP_REASON_KEY].
 */
internal enum class ArgumentDropReason(
  val serializedValue: String,
  val exhaustsUpdateBudget: Boolean = false,
) {

  CHARACTER_LIMIT("max_character_limit_exceeded", exhaustsUpdateBudget = true),
  DUPLICATE_KEY("duplicate_key"),
  INVALID_STRUCTURE("invalid_structure"),
  MAX_COUNT("max_argument_count_exceeded", exhaustsUpdateBudget = true),
  SANITIZATION_FAILED("sanitization_failed");

  companion object {

    const val ARGUMENT_DROP_REASON_KEY = "dropped_by_sentry"
  }
}

internal object ArgumentConstraints {

  /**
   * Max combined length of map keys and text values visited in one back stack update.
   *
   * If exceeded, the entry that overflows loses its arguments, as do older entries; newer entries
   * are preserved.
   *
   * Caps payload size in the presence of large individual arguments.
   */
  const val MAX_ARGUMENT_CHARACTERS = 4_096

  /**
   * Max depth of an argument value within one back stack entry. Root arguments have depth 1; each
   * nested map or list adds one level.
   *
   * For instance, `mapOf("id" to 123)` reaches a depth of 1; `mapOf("items" to listOf("apple",
   * "banana"))` reaches a depth of 2.
   *
   * If exceeded, all arguments for that back stack entry are dropped.
   */
  const val MAX_ARGUMENT_DEPTH = 10

  /**
   * Max number of argument values visited in one back stack update. Each scalar or nested container
   * counts as one value; map keys and each entry's root argument map are excluded.
   *
   * For instance, `mapOf("id" to 123)` consumes 1 value; `mapOf("profile" to mapOf("id" to 123,
   * "name" to "Ada"))` consumes 3 values.
   *
   * If exceeded, the entry that overflows loses its arguments, as do older entries; newer entries
   * are preserved.
   *
   * For instance, suppose we have the following back stack:
   *
   * - /Checkout -> Top of the stack and processed first
   * - /ProductDetail -> Processed second and exceeds the `MAX_ARGUMENT_VALUES` budget
   * - /Home
   *
   * Then /ProductDetail and /Home will have no arguments, but /Checkout will.
   */
  const val MAX_ARGUMENT_VALUES = 200
}
