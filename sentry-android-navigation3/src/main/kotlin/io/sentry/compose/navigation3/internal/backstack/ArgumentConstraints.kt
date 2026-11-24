package io.sentry.compose.navigation3.internal.backstack

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
