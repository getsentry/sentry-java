package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.contract.ArgumentDropReason
import io.sentry.compose.navigation3.internal.contract.ArgumentDropReason.Companion.ARGUMENT_DROP_REASON_KEY
import io.sentry.compose.navigation3.internal.contract.UNKNOWN_ENTRY_NAME
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason
import org.junit.Test

class NormalizedSentryBackStackEntryTest {

  @Test
  fun `formatName() - adds a leading slash to a name without one`() {
    assertThat(NormalizedSentryBackStackEntry.formatName("profile")).isEqualTo("/profile")
  }

  @Test
  fun `formatName() - keeps the leading slash of a name that has one`() {
    assertThat(NormalizedSentryBackStackEntry.formatName("/profile")).isEqualTo("/profile")
  }

  @Test
  fun `formatName() - trims whitespace around the name`() {
    assertThat(NormalizedSentryBackStackEntry.formatName("  /profile ")).isEqualTo("/profile")
  }

  @Test
  fun `formatName() - returns an empty name for a blank name`() {
    assertThat(NormalizedSentryBackStackEntry.formatName("   ")).isEmpty()
  }

  @Test
  fun `serialize() - returns the name and arguments of a named entry`() {
    val entry = NormalizedSentryBackStackEntry("/SettingsScreen", mapOf("section" to "privacy"))

    assertThat(entry.serialize())
      .isEqualTo(
        mapOf("entry" to "/SettingsScreen", "entry_arguments" to mapOf("section" to "privacy")),
      )
  }

  @Test
  fun `serialize() - returns only the name of a named entry without arguments`() {
    val entry = NormalizedSentryBackStackEntry(name = "/HomeScreen")

    assertThat(entry.serialize()).isEqualTo(mapOf("entry" to "/HomeScreen"))
  }

  @Test
  fun `serialize() - includes the unknown entry reason of an unnamed entry`() {
    val entry =
      NormalizedSentryBackStackEntry(
        name = UNKNOWN_ENTRY_NAME,
        arguments = mapOf("userId" to "123"),
        unknownEntryReason = UnknownEntryReason.BLANK_NAME,
      )

    assertThat(entry.serialize())
      .isEqualTo(
        mapOf(
          "entry" to UNKNOWN_ENTRY_NAME,
          "unknown_entry_reason" to "blank_name",
          "entry_arguments" to mapOf("userId" to "123"),
        ),
      )
  }

  @Test
  fun `serialize() - includes the reason arguments were dropped`() {
    val entry =
      NormalizedSentryBackStackEntry(
        name = "/HomeScreen",
        argumentDropReason = ArgumentDropReason.MAX_COUNT,
      )

    assertThat(entry.serialize())
      .isEqualTo(
        mapOf(
          "entry" to "/HomeScreen",
          "entry_arguments" to mapOf(ARGUMENT_DROP_REASON_KEY to "max_argument_count_exceeded"),
        ),
      )
  }

  @Test
  fun `transactionData() - returns the arguments of an entry with arguments`() {
    val entry = NormalizedSentryBackStackEntry("/HomeScreen", mapOf("tab" to "home"))

    assertThat(entry.transactionData()).isEqualTo(mapOf("arguments" to mapOf("tab" to "home")))
  }

  @Test
  fun `transactionData() - returns no data for a named entry without arguments`() {
    val entry = NormalizedSentryBackStackEntry("/HomeScreen")

    assertThat(entry.transactionData()).isEmpty()
  }

  @Test
  fun `transactionData() - returns the unknown entry reason of an unnamed entry without arguments`() {
    val entry =
      NormalizedSentryBackStackEntry(
        UNKNOWN_ENTRY_NAME,
        unknownEntryReason = UnknownEntryReason.UNMAPPED,
      )

    assertThat(entry.transactionData()).isEqualTo(mapOf("unknown_entry_reason" to "unmapped"))
  }

  @Test
  fun `transactionData() - returns the arguments and unknown entry reason of an unnamed entry with arguments`() {
    val entry =
      NormalizedSentryBackStackEntry(
        UNKNOWN_ENTRY_NAME,
        arguments = mapOf("userId" to "123"),
        unknownEntryReason = UnknownEntryReason.BLANK_NAME,
      )

    assertThat(entry.transactionData())
      .isEqualTo(
        mapOf("arguments" to mapOf("userId" to "123"), "unknown_entry_reason" to "blank_name"),
      )
  }

  @Test
  fun `transactionData() - includes the reason arguments were dropped in the arguments`() {
    val entry =
      NormalizedSentryBackStackEntry(
        "/HomeScreen",
        arguments = mapOf("tab" to "home"),
        argumentDropReason = ArgumentDropReason.CHARACTER_LIMIT,
      )

    assertThat(entry.transactionData())
      .isEqualTo(
        mapOf(
          "arguments" to
            mapOf(
              "tab" to "home",
              ARGUMENT_DROP_REASON_KEY to ArgumentDropReason.CHARACTER_LIMIT.serializedValue,
            ),
        ),
      )
  }
}
