package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RouteTranslatorTest {

  private data class HomeRoute(val id: String = "home")

  private data class ProfileRoute(val userId: String)

  private data class SettingsRoute(val section: String)

  private enum class PrivacyMode {
    PUBLIC,
    PRIVATE,
  }

  private val logger = mock<ILogger>()

  private fun getSut(
    nameExtractor: ((Any) -> String)? = null,
    argumentsExtractor: ((Any) -> Map<String, Any?>)? = null,
    maxCapturedBackStackEntries: Int = 30,
  ): RouteTranslator<Any> =
    RouteTranslator(
      resolvers = { RouteResolvers(nameExtractor, argumentsExtractor) },
      maxCapturedBackStackEntries = maxCapturedBackStackEntries,
      logger = logger,
    )

  @Test
  fun `toRouteEntries returns the newest captured entries first`() {
    val sut = getSut(maxCapturedBackStackEntries = 2)

    val stack =
      sut.toRouteEntries(listOf(HomeRoute(), ProfileRoute("123"), SettingsRoute("privacy")))

    assertThat(stack.map { it["route"] })
      .containsExactly("/SettingsRoute", "/ProfileRoute")
      .inOrder()
  }

  @Test
  fun `toRouteEntries preserves newer entry arguments when the shared budget overflows`() {
    val sut =
      getSut(
        argumentsExtractor = { key ->
          when (key) {
            is HomeRoute -> mapOf("home" to true)
            is ProfileRoute -> mapOf("values" to List(999) { it })
            is SettingsRoute -> mapOf("section" to key.section)
            else -> emptyMap()
          }
        }
      )

    val stack =
      sut.toRouteEntries(listOf(HomeRoute(), ProfileRoute("123"), SettingsRoute("privacy")))

    assertThat(stack).hasSize(3)
    assertThat(stack[0])
      .isEqualTo(mapOf("route" to "/SettingsRoute", "args" to mapOf("section" to "privacy")))
    assertThat(stack[1]["route"]).isEqualTo("/ProfileRoute")
    assertNull(stack[1]["args"])
    assertThat(stack[2]["route"]).isEqualTo("/HomeRoute")
    assertNull(stack[2]["args"])
  }

  @Test
  fun `toRouteEntries returns no entries when capture limit is zero`() {
    val sut = getSut(maxCapturedBackStackEntries = 0)

    assertThat(sut.toRouteEntries(listOf(HomeRoute(), ProfileRoute("123")))).isEmpty()
  }

  @Test
  fun `resolveRouteName normalizes a custom name with a leading slash`() {
    val sut = getSut(nameExtractor = { "profile" })

    assertEquals("/profile", sut.resolveRouteName(ProfileRoute("123")))
  }

  @Test
  fun `resolveRouteName leaves leading slash on custom name if already present`() {
    val sut = getSut(nameExtractor = { "/profile" })

    assertEquals("/profile", sut.resolveRouteName(ProfileRoute("123")))
  }

  @Test
  fun `resolveRouteName falls back to class simple name when no name extractor is configured`() {
    val sut = getSut()

    assertEquals("/HomeRoute", sut.resolveRouteName(HomeRoute()))
  }

  @Test
  fun `resolveRouteName falls back to class simple name when name extractor throws`() {
    val sut = getSut(nameExtractor = { error("boom") })

    assertEquals("/HomeRoute", sut.resolveRouteName(HomeRoute()))
    verify(logger)
      .log(
        eq(WARNING),
        eq(
          "Nav3 nameExtractor threw while resolving a route name. Falling back to class simpleName."
        ),
        org.mockito.kotlin.any<Throwable>(),
      )
  }

  @Test
  fun `resolveArguments returns supported values in serializable form`() {
    val sut =
      getSut(
        argumentsExtractor = { _ ->
          val text = StringBuilder("hello")
          mapOf(
            "str" to "hello",
            "charSequence" to text,
            "char" to 'x',
            "num" to 42,
            "bool" to true,
            "enum" to PrivacyMode.PRIVATE,
            "nil" to null,
            "nested" to mapOf("inner" to "value"),
            "tags" to listOf("a", "b", "c"),
            "array" to arrayOf("a", 1, false, PrivacyMode.PUBLIC, 'z'),
            "ints" to intArrayOf(1, 2, 3),
            "chars" to charArrayOf('a', 'b'),
            "bytes" to byteArrayOf(4, 5),
          )
        }
      )

    assertThat(sut.resolveArguments(HomeRoute()))
      .isEqualTo(
        mapOf(
          "str" to "hello",
          "charSequence" to "hello",
          "char" to "x",
          "num" to 42,
          "bool" to true,
          "enum" to "PRIVATE",
          "nil" to null,
          "nested" to mapOf("inner" to "value"),
          "tags" to listOf("a", "b", "c"),
          "array" to listOf("a", 1, false, "PUBLIC", "z"),
          "ints" to listOf(1, 2, 3),
          "chars" to listOf("a", "b"),
          "bytes" to listOf(4.toByte(), 5.toByte()),
        )
      )
  }

  @Test
  fun `resolveArguments sanitizes nested supported containers recursively`() {
    val sut =
      getSut(
        argumentsExtractor = { _ ->
          mapOf(
            "nested" to
              mapOf(
                "items" to
                  arrayOf(
                    StringBuilder("x"),
                    listOf('y', PrivacyMode.PRIVATE),
                    booleanArrayOf(true, false),
                    charArrayOf('q'),
                  )
              )
          )
        }
      )

    assertThat(sut.resolveArguments(HomeRoute()))
      .isEqualTo(
        mapOf(
          "nested" to
            mapOf("items" to listOf("x", listOf("y", "PRIVATE"), listOf(true, false), listOf("q")))
        )
      )
  }

  @Test
  fun `resolveArguments coerces unsupported values to strings`() {
    class OpaqueValue {
      override fun toString(): String = "opaque-value"
    }

    val sut = getSut(argumentsExtractor = { _ -> mapOf("bad" to OpaqueValue()) })

    assertThat(sut.resolveArguments(HomeRoute())).isEqualTo(mapOf("bad" to "opaque-value"))
  }

  @Test
  fun `unsupported value warning is logged once per shared update state across direct and batch reads`() {
    class OpaqueValue {
      override fun toString(): String = "opaque-value"
    }

    val sut = getSut(argumentsExtractor = { _ -> mapOf("bad" to OpaqueValue()) })
    val updateWarningState = RouteTranslator.UpdateWarningState()

    sut.resolveArguments(HomeRoute(), updateWarningState)
    sut.toRouteEntries(listOf(HomeRoute(), ProfileRoute("123")), updateWarningState)

    verify(logger, times(1))
      .log(
        eq(WARNING),
        eq(
          "Nav3 argumentsExtractor returned unsupported value of type %s while processing this " +
            "back stack update. Falling back to toString(). Use String, CharSequence, Char, " +
            "Number, Boolean, Enum, Map, Collection, object Array, and primitive array values " +
            "for reliable results."
        ),
        eq("OpaqueValue"),
      )
  }

  @Test
  fun `unsupported value warning can recur with a fresh update state`() {
    class OpaqueValue {
      override fun toString(): String = "opaque-value"
    }

    val sut = getSut(argumentsExtractor = { _ -> mapOf("bad" to OpaqueValue()) })

    sut.resolveArguments(HomeRoute(), RouteTranslator.UpdateWarningState())
    clearInvocations(logger)

    sut.resolveArguments(HomeRoute(), RouteTranslator.UpdateWarningState())

    verify(logger, times(1))
      .log(
        eq(WARNING),
        eq(
          "Nav3 argumentsExtractor returned unsupported value of type %s while processing this " +
            "back stack update. Falling back to toString(). Use String, CharSequence, Char, " +
            "Number, Boolean, Enum, Map, Collection, object Array, and primitive array values " +
            "for reliable results."
        ),
        eq("OpaqueValue"),
      )
  }

  @Test
  fun `resolveArguments returns empty if no arguments extractor`() {
    val sut = getSut(argumentsExtractor = null)

    assertThat(sut.resolveArguments(HomeRoute())).isEmpty()
  }

  @Test
  fun `resolveArguments returns empty when arguments extractor throws`() {
    val sut = getSut(argumentsExtractor = { error("boom") })

    assertThat(sut.resolveArguments(HomeRoute())).isEmpty()
    verify(logger)
      .log(
        eq(WARNING),
        eq("Nav3 argumentsExtractor threw while resolving arguments. Skipping arguments."),
        org.mockito.kotlin.any<Throwable>(),
      )
  }

  @Test
  fun `resolveArguments returns empty for cyclic structures`() {
    val cyclic = mutableMapOf<String, Any?>()
    cyclic["self"] = cyclic

    val sut = getSut(argumentsExtractor = { _ -> mapOf("cyclic" to cyclic) })

    assertThat(sut.resolveArguments(HomeRoute())).isEmpty()
  }

  @Test
  fun `resolveArguments returns empty for deeply nested structures`() {
    var nested: Any? = "value"
    repeat(25) { nested = listOf(nested) }

    val sut = getSut(argumentsExtractor = { _ -> mapOf("nested" to nested) })

    assertThat(sut.resolveArguments(ProfileRoute("123"))).isEmpty()
  }

  @Test
  fun `resolveArguments drops oversized payloads instead of truncating them`() {
    val sut = getSut(argumentsExtractor = { _ -> mapOf("values" to List(1_001) { it }) })

    assertThat(sut.resolveArguments(HomeRoute())).isEmpty()
  }
}
