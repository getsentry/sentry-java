package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.JsonSerializer
import io.sentry.NoOpLogger
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter
import io.sentry.compose.navigation3.internal.backstack.ForwardingBackStackEntryMapper
import java.io.StringWriter
import org.junit.Test

class ArgumentNumberSerializationTest {
  @Test
  fun `non-finite numbers produce valid navigation argument JSON`() {
    val mapper =
      BackStackEntryMapper<Any> {
        SentryBackStackEntry(
          "Home",
          mapOf(
            "price" to Double.NaN,
            "limits" to floatArrayOf(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY),
          ),
        )
      }
    val converter =
      BackStackConverter(ForwardingBackStackEntryMapper { mapper }, NoOpLogger.getInstance())
    val entry =
      converter.convert(listOf(Any()), BackStackConverter.RetentionPolicy.KEEP_FIRST).single()
    val json = StringWriter()
    JsonSerializer(SentryOptions()).serialize(entry.serialize(), json)
    assertThat(json.toString())
      .isEqualTo(
        """{"entry":"/Home","entry_arguments":{"price":"NaN","limits":["Infinity","-Infinity"]}}""",
      )
  }

  @Test
  fun `non-finite number text spends the character budget`() {
    val sanitizer =
      BackStackConverter.ArgumentSanitizer(
        NoOpLogger.getInstance(),
        BackStackConverter.WarningState(),
      )
    val values = mapOf("x" to "a".repeat(4_096 - 3), "n" to Double.NaN)
    assertThat(sanitizer.sanitizeEntry(values).dropReason)
      .isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
  }

  @Test
  fun `non-finite navigation arguments keep the containing SentryEvent serializable`() {
    val mapper =
      BackStackEntryMapper<Any> {
        SentryBackStackEntry("Home", mapOf("price" to Double.NaN))
      }
    val converter =
      BackStackConverter(ForwardingBackStackEntryMapper { mapper }, NoOpLogger.getInstance())
    val entry =
      converter.convert(listOf(Any()), BackStackConverter.RetentionPolicy.KEEP_FIRST).single()
    val event =
      SentryEvent().apply {
        contexts["navigation"] = mapOf("backstack" to listOf(entry.serialize()))
        breadcrumbs = listOf(Breadcrumb().apply { data["to_arguments"] = entry.arguments })
      }
    val json = StringWriter()

    JsonSerializer(SentryOptions()).serialize(event, json)

    assertThat(json.toString()).contains(""""entry_arguments":{"price":"NaN"}""")
    assertThat(json.toString()).contains(""""to_arguments":{"price":"NaN"}""")
  }
}
