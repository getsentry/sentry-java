package io.sentry.compose.navigation3.internal.contract

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.contract.ArgumentDropReason.Companion.ARGUMENT_DROP_REASON_KEY
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason.Companion.UNKNOWN_ENTRY_REASON_KEY
import org.junit.Test

/** Pins the strings the Sentry UI displays, so that renaming one fails a test. */
class NavDataContractTest {

  @Test
  fun `navigation context keys match the displayed names`() {
    assertThat(NAVIGATION_CONTEXT_KEY).isEqualTo("Navigation")
    assertThat(BACK_STACK_KEY).isEqualTo("Back Stack")
  }

  @Test
  fun `back stack entry keys match the displayed names`() {
    assertThat(BACK_STACK_ENTRY_KEY).isEqualTo("entry")
    assertThat(BACK_STACK_ENTRY_ARGUMENTS_KEY).isEqualTo("entry_arguments")
  }

  @Test
  fun `transaction arguments key matches the displayed name`() {
    assertThat(TRANSACTION_ARGUMENTS_KEY).isEqualTo("arguments")
  }

  @Test
  fun `breadcrumb keys match the displayed names`() {
    assertThat(BREADCRUMB_FROM_KEY).isEqualTo("from")
    assertThat(BREADCRUMB_TO_KEY).isEqualTo("to")
    assertThat(BREADCRUMB_FROM_ARGUMENTS_KEY).isEqualTo("from_arguments")
    assertThat(BREADCRUMB_TO_ARGUMENTS_KEY).isEqualTo("to_arguments")
    assertThat(BREADCRUMB_FROM_UNKNOWN_ENTRY_REASON_KEY).isEqualTo("from_unknown_entry_reason")
    assertThat(BREADCRUMB_TO_UNKNOWN_ENTRY_REASON_KEY).isEqualTo("to_unknown_entry_reason")
  }

  @Test
  fun `navigation operation and transaction origin match the displayed values`() {
    assertThat(NAVIGATION_OP).isEqualTo("navigation")
    assertThat(NAVIGATION_TRANSACTION_ORIGIN).isEqualTo("auto.navigation.nav3")
  }

  @Test
  fun `unknown entry name matches the displayed name`() {
    assertThat(UNKNOWN_ENTRY_NAME).isEqualTo("/unknown")
  }

  @Test
  fun `unknown entry reason key matches the displayed name`() {
    assertThat(UNKNOWN_ENTRY_REASON_KEY).isEqualTo("unknown_entry_reason")
  }

  @Test
  fun `each unknown entry reason serializes to its displayed value`() {
    val serializedValues = UnknownEntryReason.entries.associateWith { it.serializedValue }

    assertThat(serializedValues)
      .containsExactly(
        UnknownEntryReason.BLANK_NAME,
        "blank_name",
        UnknownEntryReason.MAPPING_FAILED,
        "mapping_failed",
        UnknownEntryReason.UNMAPPED,
        "unmapped",
      )
  }

  @Test
  fun `argument drop reason key matches the displayed name`() {
    assertThat(ARGUMENT_DROP_REASON_KEY).isEqualTo("dropped_by_sentry")
  }

  @Test
  fun `each argument drop reason serializes to its displayed value`() {
    val serializedValues = ArgumentDropReason.entries.associateWith { it.serializedValue }

    assertThat(serializedValues)
      .containsExactly(
        ArgumentDropReason.CHARACTER_LIMIT,
        "max_character_limit_exceeded",
        ArgumentDropReason.DUPLICATE_KEY,
        "duplicate_key",
        ArgumentDropReason.INVALID_STRUCTURE,
        "invalid_structure",
        ArgumentDropReason.MAX_COUNT,
        "max_argument_count_exceeded",
        ArgumentDropReason.SANITIZATION_FAILED,
        "sanitization_failed",
      )
  }
}
