package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.RouteTranslator.ArgumentSanitizer
import io.sentry.compose.navigation3.RouteTranslator.RetentionPolicy
import io.sentry.compose.navigation3.RouteTranslator.WarningState
import java.util.AbstractCollection
import org.junit.Test
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RouteTranslatorTest {

  private data class HomeRoute(val id: String = "home")

  private data class ProfileRoute(val userId: String)

  private data class ProductRoute(val productId: String)

  private data class SettingsRoute(val section: String)

  private enum class PrivacyMode {
    PUBLIC,
    PRIVATE,
  }

  private val logger = mock<ILogger>()
  private val defaultNameExtractor =
    RouteNameExtractor<Any> { entry -> entry::class.simpleName ?: "unknown" }

  private fun getSut(
    nameExtractor: RouteNameExtractor<Any> = defaultNameExtractor,
    argumentsExtractor: RouteArgumentsExtractor<Any>? = null,
  ): RouteTranslator<Any> =
    RouteTranslator(
      extractors = { RouteExtractors(nameExtractor, argumentsExtractor) },
      logger = logger,
    )

  @Test
  fun `translate preserves input order`() {
    val sut = getSut()

    val routes =
      sut.translate(
        listOf(SettingsRoute("privacy"), ProfileRoute("123"), HomeRoute()),
        RetentionPolicy.KEEP_FIRST,
      )

    assertThat(routes)
      .containsExactly(Route("/SettingsRoute"), Route("/ProfileRoute"), Route("/HomeRoute"))
      .inOrder()
  }

  @Test
  fun `translate preserves input order when top entry is last`() {
    val sut = getSut()

    val routes =
      sut.translate(
        listOf(HomeRoute(), ProfileRoute("123"), SettingsRoute("privacy")),
        RetentionPolicy.KEEP_LAST,
      )

    assertThat(routes)
      .containsExactly(Route("/HomeRoute"), Route("/ProfileRoute"), Route("/SettingsRoute"))
      .inOrder()
  }

  @Test
  fun `translate returns empty routes for an empty back stack`() {
    val sut = getSut()

    assertThat(sut.translate(emptyList(), RetentionPolicy.KEEP_FIRST)).isEmpty()
  }

  @Test
  fun `translate returns empty routes for an empty back stack when top entry is last`() {
    val sut = getSut()

    assertThat(sut.translate(emptyList(), RetentionPolicy.KEEP_LAST)).isEmpty()
  }

  @Test
  fun `translate with KEEP_FIRST preserves arguments nearest index zero when budget is exceeded`() {
    val first = SettingsRoute("privacy")
    val middle = ProfileRoute("123")
    val last = HomeRoute()
    val sut =
      getSut(
        argumentsExtractor =
          RouteArgumentsExtractor { key ->
            when (key) {
              is SettingsRoute -> mapOf("section" to key.section)
              is ProfileRoute -> mapOf("values" to List(999) { it })
              is HomeRoute -> mapOf("home" to true)
              else -> emptyMap()
            }
          }
      )

    val routes = sut.translate(listOf(first, middle, last), RetentionPolicy.KEEP_FIRST)

    assertThat(routes)
      .containsExactly(
        Route("/SettingsRoute", mapOf("section" to "privacy")),
        Route("/ProfileRoute"),
        Route("/HomeRoute"),
      )
      .inOrder()
  }

  @Test
  fun `translate with KEEP_LAST preserves arguments nearest lastIndex when budget is exceeded`() {
    val first = HomeRoute()
    val middle = ProfileRoute("123")
    val last = SettingsRoute("privacy")
    val sut =
      getSut(
        argumentsExtractor =
          RouteArgumentsExtractor { key ->
            when (key) {
              is HomeRoute -> mapOf("home" to true)
              is ProfileRoute -> mapOf("values" to List(999) { it })
              is SettingsRoute -> mapOf("section" to key.section)
              else -> emptyMap()
            }
          }
      )

    val routes = sut.translate(listOf(first, middle, last), RetentionPolicy.KEEP_LAST)

    assertThat(routes)
      .containsExactly(
        Route("/HomeRoute"),
        Route("/ProfileRoute"),
        Route("/SettingsRoute", mapOf("section" to "privacy")),
      )
      .inOrder()
  }

  @Test
  fun `translate with KEEP_FIRST treats entries as distinct by position even if structurally equal`() {
    val first = ProductRoute("sku-1")
    val middle = ProfileRoute("123")
    val last = ProductRoute("sku-1")
    val sut =
      getSut(
        argumentsExtractor =
          RouteArgumentsExtractor { entry ->
            when (entry) {
              is ProductRoute -> mapOf("productId" to entry.productId)
              is ProfileRoute -> mapOf("values" to List(999) { it })
              else -> emptyMap()
            }
          }
      )

    val routes = sut.translate(listOf(first, middle, last), RetentionPolicy.KEEP_FIRST)

    assertThat(routes)
      .containsExactly(
        Route("/ProductRoute", mapOf("productId" to "sku-1")),
        Route("/ProfileRoute"),
        Route("/ProductRoute"),
      )
      .inOrder()
  }

  @Test
  fun `translate with KEEP_LAST treats entries as distinct by position even if structurally equal`() {
    val first = ProductRoute("sku-1")
    val middle = ProfileRoute("123")
    val last = ProductRoute("sku-1")
    val sut =
      getSut(
        argumentsExtractor =
          RouteArgumentsExtractor { entry ->
            when (entry) {
              is ProductRoute -> mapOf("productId" to entry.productId)
              is ProfileRoute -> mapOf("values" to List(999) { it })
              else -> emptyMap()
            }
          }
      )

    val routes = sut.translate(listOf(first, middle, last), RetentionPolicy.KEEP_LAST)

    assertThat(routes)
      .containsExactly(
        Route("/ProductRoute"),
        Route("/ProfileRoute"),
        Route("/ProductRoute", mapOf("productId" to "sku-1")),
      )
      .inOrder()
  }

  @Test
  fun `translate returns translated routes from one pass`() {
    val sut =
      getSut(
        argumentsExtractor =
          RouteArgumentsExtractor { entry ->
            when (entry) {
              is HomeRoute -> mapOf("tab" to entry.id)
              is ProfileRoute -> mapOf("userId" to entry.userId)
              else -> emptyMap()
            }
          }
      )

    val routes =
      sut.translate(
        listOf(SettingsRoute("privacy"), ProfileRoute("123")),
        RetentionPolicy.KEEP_FIRST,
      )

    assertThat(routes)
      .containsExactly(
        Route("/SettingsRoute"),
        Route("/ProfileRoute", mapOf("userId" to "123")),
      )
      .inOrder()
  }

  @Test
  fun `route serializes to back stack entry shape`() {
    val sut =
      getSut(
        argumentsExtractor =
          RouteArgumentsExtractor { entry ->
            when (entry) {
              is HomeRoute -> mapOf("tab" to entry.id)
              is ProfileRoute -> emptyMap()
              is SettingsRoute -> mapOf("section" to entry.section)
              else -> emptyMap()
            }
          }
      )

    val routes =
      sut.translate(
        listOf(SettingsRoute("privacy"), ProfileRoute("123")),
        RetentionPolicy.KEEP_FIRST,
      )

    assertThat(routes.map(Route::serialize))
      .containsExactly(
        mapOf("route" to "/SettingsRoute", "args" to mapOf("section" to "privacy")),
        mapOf("route" to "/ProfileRoute"),
      )
      .inOrder()
  }

  @Test
  fun `extractRouteName normalizes a custom name with a leading slash`() {
    val sut = getSut(nameExtractor = { "profile" })

    assertThat(sut.extractRouteName(ProfileRoute("123"), WarningState())).isEqualTo("/profile")
  }

  @Test
  fun `extractRouteName leaves leading slash on custom name if already present`() {
    val sut = getSut(nameExtractor = { "/profile" })

    assertThat(sut.extractRouteName(ProfileRoute("123"), WarningState())).isEqualTo("/profile")
  }

  @Test
  fun `extractRouteName returns the configured name extractor result`() {
    val sut = getSut()

    assertThat(sut.extractRouteName(HomeRoute(), WarningState())).isEqualTo("/HomeRoute")
  }

  @Test
  fun `extractRouteName returns unknown when name extractor throws`() {
    val sut = getSut(nameExtractor = { error("boom") })

    assertThat(sut.extractRouteName(HomeRoute(), WarningState()))
      .isEqualTo(RouteTranslator.UNKNOWN_ROUTE_NAME)
    verify(logger)
      .log(
        eq(WARNING),
        eq("Nav3 nameExtractor threw while resolving a route name. Using /unknown instead."),
        org.mockito.kotlin.any<Throwable>(),
      )
  }

  @Test
  fun `extractRouteName returns unknown when name extractor returns blank`() {
    val sut = getSut(nameExtractor = { "   " })

    assertThat(sut.extractRouteName(HomeRoute(), WarningState()))
      .isEqualTo(RouteTranslator.UNKNOWN_ROUTE_NAME)
    verify(logger)
      .log(
        eq(WARNING),
        eq(
          "Nav3 nameExtractor returned a blank route name while processing this back stack update. " +
            "Using /unknown instead."
        ),
      )
  }

  @Test
  fun `extractRouteArguments returns supported values in serializable form`() {
    val sut =
      getSut(
        argumentsExtractor =
          RouteArgumentsExtractor { _ ->
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

    assertThat(sut.extractRouteArguments(HomeRoute(), ArgumentSanitizer(logger, WarningState())))
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
  fun `extractRouteArguments sanitizes nested supported containers recursively`() {
    val sut =
      getSut(
        argumentsExtractor =
          RouteArgumentsExtractor { _ ->
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

    assertThat(sut.extractRouteArguments(HomeRoute(), ArgumentSanitizer(logger, WarningState())))
      .isEqualTo(
        mapOf(
          "nested" to
            mapOf("items" to listOf("x", listOf("y", "PRIVATE"), listOf(true, false), listOf("q")))
        )
      )
  }

  @Test
  fun `extractRouteArguments coerces unsupported values to strings`() {
    class OpaqueValue {
      override fun toString(): String = "opaque-value"
    }

    val sut =
      getSut(argumentsExtractor = RouteArgumentsExtractor { _ -> mapOf("bad" to OpaqueValue()) })

    assertThat(sut.extractRouteArguments(HomeRoute(), ArgumentSanitizer(logger, WarningState())))
      .isEqualTo(mapOf("bad" to "opaque-value"))
  }

  @Test
  fun `translate logs unsupported value warning once per back stack update`() {
    class OpaqueValue {
      override fun toString(): String = "opaque-value"
    }

    val sut =
      getSut(
        argumentsExtractor =
          RouteArgumentsExtractor { entry ->
            when (entry) {
              is HomeRoute -> mapOf("bad" to OpaqueValue())
              is ProfileRoute -> mapOf("alsoBad" to OpaqueValue())
              else -> emptyMap()
            }
          }
      )

    sut.translate(listOf(HomeRoute(), ProfileRoute("123")), RetentionPolicy.KEEP_FIRST)

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

    val sut =
      getSut(argumentsExtractor = RouteArgumentsExtractor { _ -> mapOf("bad" to OpaqueValue()) })

    sut.extractRouteArguments(HomeRoute(), ArgumentSanitizer(logger, WarningState()))
    clearInvocations(logger)

    sut.extractRouteArguments(HomeRoute(), ArgumentSanitizer(logger, WarningState()))

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
  fun `extractRouteArguments returns empty if no arguments extractor`() {
    val sut = getSut(argumentsExtractor = null)

    assertThat(sut.extractRouteArguments(HomeRoute(), ArgumentSanitizer(logger, WarningState())))
      .isEmpty()
  }

  @Test
  fun `extractRouteArguments returns empty when arguments extractor throws`() {
    val sut = getSut(argumentsExtractor = { error("boom") })

    assertThat(sut.extractRouteArguments(HomeRoute(), ArgumentSanitizer(logger, WarningState())))
      .isEmpty()
    verify(logger)
      .log(
        eq(WARNING),
        eq("Nav3 argumentsExtractor threw while resolving arguments. Skipping arguments."),
        org.mockito.kotlin.any<Throwable>(),
      )
  }

  @Test
  fun `extractRouteArguments returns empty for cyclic structures`() {
    val cyclic = mutableMapOf<String, Any?>()
    cyclic["self"] = cyclic

    val sut =
      getSut(argumentsExtractor = RouteArgumentsExtractor { _ -> mapOf("cyclic" to cyclic) })

    assertThat(sut.extractRouteArguments(HomeRoute(), ArgumentSanitizer(logger, WarningState())))
      .isEmpty()
  }

  @Test
  fun `extractRouteArguments returns empty for deeply nested structures`() {
    var nested: Any? = "value"
    repeat(25) { nested = listOf(nested) }

    val sut =
      getSut(argumentsExtractor = RouteArgumentsExtractor { _ -> mapOf("nested" to nested) })

    assertThat(
        sut.extractRouteArguments(ProfileRoute("123"), ArgumentSanitizer(logger, WarningState()))
      )
      .isEmpty()
  }

  @Test
  fun `extractRouteArguments drops oversized payloads instead of truncating them`() {
    val sut =
      getSut(
        argumentsExtractor = RouteArgumentsExtractor { _ -> mapOf("values" to List(1_001) { it }) }
      )

    assertThat(sut.extractRouteArguments(HomeRoute(), ArgumentSanitizer(logger, WarningState())))
      .isEmpty()
  }

  @Test
  fun `extractRouteArguments does not use caller collection size for allocation`() {
    val values =
      object : AbstractCollection<Int>() {
        var wasSizeRead = false

        override val size: Int
          get() {
            wasSizeRead = true
            return 2
          }

        override fun iterator(): MutableIterator<Int> = mutableListOf(1, 2).iterator()
      }
    val sut =
      getSut(argumentsExtractor = RouteArgumentsExtractor { _ -> mapOf("values" to values) })

    assertThat(sut.extractRouteArguments(HomeRoute(), ArgumentSanitizer(logger, WarningState())))
      .isEqualTo(mapOf("values" to listOf(1, 2)))
    assertThat(values.wasSizeRead).isFalse()
  }
}
