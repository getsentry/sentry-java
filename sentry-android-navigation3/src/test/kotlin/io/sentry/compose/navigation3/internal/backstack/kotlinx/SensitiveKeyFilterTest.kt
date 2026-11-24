package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.SensitiveKeyFilter
import io.sentry.util.UrlUtils.SENSITIVE_DATA_SUBSTITUTE
import kotlin.test.assertFailsWith
import org.junit.Test

class SensitiveKeyFilterTest {

  private val sink = TreeArgumentWriter(ArgumentBudget())
  private val filter = SensitiveKeyFilter(sink)

  // Keys, values, maps, and lists under non-sensitive keys

  @Test
  fun `key() - passes non-sensitive keys and the scalars, maps, and lists under them to the sink`() {
    filter.key("query")
    filter.scalar("shoes")
    filter.key("filters")
    filter.beginMap()
    filter.key("brands")
    filter.beginList()
    filter.scalar("a")
    filter.end()
    filter.end()

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments)
      .isEqualTo(mapOf("query" to "shoes", "filters" to mapOf("brands" to listOf("a"))))
  }

  // Whether the next value is sensitive

  @Test
  fun `isNextValueSensitive - is false before any key is written`() {
    assertThat(filter.isNextValueSensitive).isFalse()
  }

  @Test
  fun `isNextValueSensitive - is true after a sensitive key is written`() {
    filter.key("password")

    assertThat(filter.isNextValueSensitive).isTrue()
  }

  @Test
  fun `isNextValueSensitive - is false after a non-sensitive key that follows a sensitive key`() {
    filter.key("password")
    filter.key("query")

    assertThat(filter.isNextValueSensitive).isFalse()
  }

  @Test
  fun `isNextValueSensitive - is false once the scalar under a sensitive key is written`() {
    filter.key("password")
    filter.scalar("hunter2")

    assertThat(filter.isNextValueSensitive).isFalse()
  }

  @Test
  fun `isNextValueSensitive - is false once a map under a sensitive key is begun`() {
    filter.key("credentials")
    filter.beginMap()

    assertThat(filter.isNextValueSensitive).isFalse()
  }

  @Test
  fun `isNextValueSensitive - is false once a list under a sensitive key is begun`() {
    filter.key("refreshTokens")
    filter.beginList()

    assertThat(filter.isNextValueSensitive).isFalse()
  }

  // Values marked sensitive

  @Test
  fun `markNextValueSensitive() - reports the next value as sensitive under a non-sensitive key`() {
    filter.key("header")

    filter.markNextValueSensitive()

    assertThat(filter.isNextValueSensitive).isTrue()
  }

  @Test
  fun `markNextValueSensitive() - writes the substitute in place of the next scalar under a non-sensitive key`() {
    filter.key("header")
    filter.markNextValueSensitive()
    filter.scalar("Bearer abc123")

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("header" to SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `markNextValueSensitive() - writes the substitute in place of the next map, discarding its contents`() {
    filter.key("header")
    filter.markNextValueSensitive()
    filter.beginMap()
    filter.key("user")
    filter.scalar("alice")
    filter.end()
    filter.key("page")
    filter.scalar(2)

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("header" to SENSITIVE_DATA_SUBSTITUTE, "page" to 2))
  }

  @Test
  fun `markNextValueSensitive() - writes the substitute in place of the next list, discarding its elements`() {
    filter.key("header")
    filter.markNextValueSensitive()
    filter.beginList()
    filter.scalar("t-1")
    filter.end()

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("header" to SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `markNextValueSensitive() - filters only the next value, writing the one after it as-is`() {
    filter.key("rows")
    filter.beginList()
    filter.markNextValueSensitive()
    filter.scalar("secret")
    filter.scalar("plain")
    filter.end()

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("rows" to listOf(SENSITIVE_DATA_SUBSTITUTE, "plain")))
  }

  @Test
  fun `markNextValueSensitive() - has no effect inside a discarded map, so the list element after it is written`() {
    filter.key("rows")
    filter.beginList()
    filter.beginMap()
    filter.key("token")
    filter.beginMap()
    filter.markNextValueSensitive()
    filter.scalar("t-1")
    filter.end()
    filter.end()
    filter.scalar("plain")
    filter.end()

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments)
      .isEqualTo(mapOf("rows" to listOf(mapOf("token" to SENSITIVE_DATA_SUBSTITUTE), "plain")))
  }

  @Test
  fun `markNextValueSensitive() - has no effect inside a discarded map, so the next key's value is written`() {
    filter.key("credentials")
    filter.beginMap()
    filter.markNextValueSensitive()
    filter.end()
    filter.key("page")
    filter.scalar(2)

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("credentials" to SENSITIVE_DATA_SUBSTITUTE, "page" to 2))
  }

  // Scalars

  @Test
  fun `scalar() - writes the substitute in place of a scalar under a sensitive key`() {
    filter.key("password")
    filter.scalar("hunter2")

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("password" to SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `scalar() - writes a scalar under a non-sensitive key that follows a sensitive key`() {
    filter.key("password")
    filter.scalar("hunter2")
    filter.key("page")
    filter.scalar(2)

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("password" to SENSITIVE_DATA_SUBSTITUTE, "page" to 2))
  }

  @Test
  fun `scalar() - writes list elements under a non-sensitive key, even if they look like sensitive keys`() {
    filter.key("tags")
    filter.beginList()
    filter.scalar("password")
    filter.scalar("token")
    filter.end()

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("tags" to listOf("password", "token")))
  }

  @Test
  fun `scalar() - writes the substitute under the sensitive keys of maps in a list`() {
    filter.key("rows")
    filter.beginList()
    filter.beginMap()
    filter.key("jwt")
    filter.scalar("t-1")
    filter.key("note")
    filter.scalar("visible")
    filter.end()
    filter.scalar("plain")
    filter.end()

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments)
      .isEqualTo(
        mapOf(
          "rows" to listOf(mapOf("jwt" to SENSITIVE_DATA_SUBSTITUTE, "note" to "visible"), "plain"),
        ),
      )
  }

  // Maps

  @Test
  fun `beginMap() - writes the substitute in place of a map under a sensitive key, discarding its nested maps, lists, and keys`() {
    filter.key("credentials")
    filter.beginMap()
    filter.key("history")
    filter.beginList()
    filter.beginMap()
    filter.key("user")
    filter.scalar("alice")
    filter.end()
    filter.end()
    filter.key("note")
    filter.scalar("visible?")
    filter.end()
    filter.key("page")
    filter.scalar(2)

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("credentials" to SENSITIVE_DATA_SUBSTITUTE, "page" to 2))
  }

  @Test
  fun `beginMap() - passes the map's identity to the sink`() {
    val map = Any()
    filter.key("filters")
    filter.beginMap(map)
    filter.key("nested")

    val drop = assertFailsWith<DropArguments> { filter.beginMap(map) }

    assertThat(drop.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
  }

  // Lists

  @Test
  fun `beginList() - writes the substitute in place of a list under a sensitive key`() {
    filter.key("refreshTokens")
    filter.beginList()
    filter.scalar("t-1")
    filter.scalar("t-2")
    filter.end()

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("refreshTokens" to SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `beginList() - passes the list's identity to the sink`() {
    val list = Any()
    filter.key("rows")
    filter.beginList(list)

    val drop = assertFailsWith<DropArguments> { filter.beginList(list) }

    assertThat(drop.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
  }

  // Ends of maps and lists

  @Test
  fun `end() - resumes writing the containing map after a discarded map ends`() {
    filter.key("filters")
    filter.beginMap()
    filter.key("token")
    filter.beginMap()
    filter.key("value")
    filter.scalar("t-1")
    filter.end()
    filter.key("color")
    filter.scalar("red")
    filter.end()
    filter.key("page")
    filter.scalar(2)

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments)
      .isEqualTo(
        mapOf(
          "filters" to mapOf("token" to SENSITIVE_DATA_SUBSTITUTE, "color" to "red"),
          "page" to 2,
        ),
      )
  }

  @Test
  fun `end() - writes a map under a non-sensitive key that follows a discarded map`() {
    filter.key("password")
    filter.beginMap()
    filter.end()
    filter.key("filters")
    filter.beginMap()
    filter.key("color")
    filter.scalar("red")
    filter.end()

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments)
      .isEqualTo(
        mapOf("password" to SENSITIVE_DATA_SUBSTITUTE, "filters" to mapOf("color" to "red")),
      )
  }

  // Rejected limits

  @Test
  fun `reject() - rejects the limit on the sink`() {
    val drop =
      assertFailsWith<DropArguments> {
        filter.reject(ArgumentDropReason.INVALID_STRUCTURE, DropArguments.STRUCTURE_WARNING)
      }

    assertThat(drop.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(sink.failure).isSameInstanceAs(drop)
  }

  @Test
  fun `reject() - rejects the limit on the sink within a map under a sensitive key`() {
    // The filter discards writes under a sensitive key, but not limits a source rejects.
    filter.key("credentials")
    filter.beginMap()

    val drop =
      assertFailsWith<DropArguments> {
        filter.reject(ArgumentDropReason.INVALID_STRUCTURE, DropArguments.STRUCTURE_WARNING)
      }

    assertThat(sink.failure).isSameInstanceAs(drop)
  }
}
