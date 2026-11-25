package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason.Companion.ARGUMENT_DROP_REASON_KEY
import io.sentry.compose.navigation3.internal.backstack.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME
import org.junit.Test

class NormalizedSentryBackStackEntryTest {

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
  fun `serialize() - includes the reason an unnamed entry has no name`() {
    val entry =
      NormalizedSentryBackStackEntry(
        name = UNKNOWN_ENTRY_NAME,
        arguments = mapOf("userId" to "123"),
        unnamedReason = UnnamedReason.BLANK_NAME,
      )

    assertThat(entry.serialize())
      .isEqualTo(
        mapOf(
          "entry" to UNKNOWN_ENTRY_NAME,
          "entry_unnamed_reason" to "blank_name",
          "entry_arguments" to mapOf("userId" to "123"),
        ),
      )
  }

  @Test
  fun `serialize() - omits the unnamed reason for a named entry`() {
    val entry = NormalizedSentryBackStackEntry(name = "/HomeScreen")

    assertThat(entry.serialize()).isEqualTo(mapOf("entry" to "/HomeScreen"))
  }

  @Test
  fun `transactionData() - returns the arguments of an entry with arguments`() {
    val entry = NormalizedSentryBackStackEntry("/HomeScreen", mapOf("tab" to "home"))

    assertThat(entry.transactionData()).isEqualTo(mapOf("arguments" to mapOf("tab" to "home")))
  }

  @Test
  fun `transactionData() - returns the unnamed reason of an unnamed entry without arguments`() {
    val entry =
      NormalizedSentryBackStackEntry(UNKNOWN_ENTRY_NAME, unnamedReason = UnnamedReason.UNMAPPED)

    assertThat(entry.transactionData()).isEqualTo(mapOf("unnamed_reason" to "unmapped"))
  }

  @Test
  fun `transactionData() - returns the arguments and unnamed reason of an unnamed entry with arguments`() {
    val entry =
      NormalizedSentryBackStackEntry(
        UNKNOWN_ENTRY_NAME,
        arguments = mapOf("userId" to "123"),
        unnamedReason = UnnamedReason.BLANK_NAME,
      )

    assertThat(entry.transactionData())
      .isEqualTo(
        mapOf("arguments" to mapOf("userId" to "123"), "unnamed_reason" to "blank_name"),
      )
  }

  @Test
  fun `transactionData() - returns no data for a named entry without arguments`() {
    val entry = NormalizedSentryBackStackEntry("/HomeScreen")

    assertThat(entry.transactionData()).isEmpty()
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
