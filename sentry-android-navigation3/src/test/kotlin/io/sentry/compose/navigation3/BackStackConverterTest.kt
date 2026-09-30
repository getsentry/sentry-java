package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.BackStackConverter.RetentionPolicy
import io.sentry.compose.navigation3.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME
import java.util.AbstractCollection
import org.junit.Test
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class BackStackConverterTest {

  private data class HomeScreen(val id: String = "home")

  private data class ProfileScreen(val userId: String, val marketingId: String = "marketing_id")

  private data class ProductScreen(val productId: String)

  private data class SettingsScreen(val section: String)

  private enum class PrivacyMode {
    PUBLIC,
    PRIVATE,
  }

  private val logger = mock<ILogger>()

  private val defaultEntryMapper =
    BackStackEntryMapper<Any> { entry ->
      SentryBackStackEntry(entry::class.simpleName ?: "unknown")
    }

  private fun getSut(
    entryMapper: BackStackEntryMapper<Any> = defaultEntryMapper
  ): BackStackConverter<Any> =
    BackStackConverter(
      entryMapper = ForwardingBackStackEntryMapper { entryMapper },
      logger = logger,
    )

  private fun entryInfo(entry: Any, arguments: Map<String, Any?>? = null): SentryBackStackEntry =
    SentryBackStackEntry(entry::class.simpleName ?: "unknown", arguments)

  private fun BackStackConverter<Any>.convert(entry: Any): NormalizedSentryBackStackEntry =
    convert(listOf(entry), RetentionPolicy.KEEP_FIRST).single()

  @Test
  fun `convert returns normalized back stack entries`() {
    val sut =
      getSut(
        entryMapper = { entry ->
          entryInfo(
            entry,
            when (entry) {
              is HomeScreen -> mapOf("renamedId" to entry.id)
              is ProfileScreen -> mapOf("userId" to entry.userId)
              is SettingsScreen -> emptyMap()
              else -> emptyMap()
            },
          )
        }
      )

    val routes =
      sut.convert(
        listOf(
          SettingsScreen(section = "privacy"),
          ProfileScreen(userId = "123", marketingId = "987"),
          HomeScreen(id = "home"),
        ),
        RetentionPolicy.KEEP_FIRST,
      )

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry(name = "/SettingsScreen"),
        NormalizedSentryBackStackEntry(
          name = "/ProfileScreen",
          arguments = mapOf("userId" to "123"),
        ),
        NormalizedSentryBackStackEntry(
          name = "/HomeScreen",
          arguments = mapOf("renamedId" to "home"),
        ),
      )
      .inOrder()
  }

  @Test
  fun `convert preserves input order when top entry is first`() {
    val sut = getSut()

    val routes =
      sut.convert(
        listOf(SettingsScreen("privacy"), ProfileScreen("123"), HomeScreen()),
        RetentionPolicy.KEEP_FIRST,
      )

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry("/SettingsScreen"),
        NormalizedSentryBackStackEntry("/ProfileScreen"),
        NormalizedSentryBackStackEntry("/HomeScreen"),
      )
      .inOrder()
  }

  @Test
  fun `convert preserves input order when top entry is last`() {
    val sut = getSut()

    val routes =
      sut.convert(
        listOf(HomeScreen(), ProfileScreen("123"), SettingsScreen("privacy")),
        RetentionPolicy.KEEP_FIRST,
      )

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry("/HomeScreen"),
        NormalizedSentryBackStackEntry("/ProfileScreen"),
        NormalizedSentryBackStackEntry("/SettingsScreen"),
      )
      .inOrder()
  }

  @Test
  fun `convert returns an empty list if provided back stack is empty`() {
    val sut = getSut()

    assertThat(sut.convert(emptyList(), RetentionPolicy.KEEP_FIRST)).isEmpty()
  }

  @Test
  fun `convert with KEEP_FIRST preserves arguments nearest index zero when sanitization budget is exceeded`() {
    val first = SettingsScreen("privacy")
    val middle = ProfileScreen("123")
    val last = HomeScreen()
    val sut =
      getSut(
        entryMapper = { key ->
          entryInfo(
            key,
            when (key) {
              is SettingsScreen -> mapOf("section" to key.section)
              is ProfileScreen -> mapOf("values" to List(999) { it })
              is HomeScreen -> mapOf("home" to true)
              else -> emptyMap()
            },
          )
        }
      )

    val routes = sut.convert(listOf(first, middle, last), RetentionPolicy.KEEP_FIRST)

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry("/SettingsScreen", mapOf("section" to "privacy")),
        NormalizedSentryBackStackEntry("/ProfileScreen"),
        NormalizedSentryBackStackEntry("/HomeScreen"),
      )
      .inOrder()
  }

  @Test
  fun `convert with KEEP_LAST preserves arguments nearest lastIndex when sanitization budget is exceeded`() {
    val first = HomeScreen()
    val middle = ProfileScreen("123")
    val last = SettingsScreen("privacy")
    val sut =
      getSut(
        entryMapper = { key ->
          entryInfo(
            key,
            when (key) {
              is HomeScreen -> mapOf("home" to true)
              is ProfileScreen -> mapOf("values" to List(999) { it })
              is SettingsScreen -> mapOf("section" to key.section)
              else -> emptyMap()
            },
          )
        }
      )

    val routes = sut.convert(listOf(first, middle, last), RetentionPolicy.KEEP_LAST)

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry("/HomeScreen"),
        NormalizedSentryBackStackEntry("/ProfileScreen"),
        NormalizedSentryBackStackEntry("/SettingsScreen", mapOf("section" to "privacy")),
      )
      .inOrder()
  }

  @Test
  fun `convert with KEEP_FIRST treats entries as distinct by position even if structurally equal`() {
    val first = ProductScreen("sku-1")
    val middle = ProfileScreen("123")
    val last = ProductScreen("sku-1")
    val sut =
      getSut(
        entryMapper = { entry ->
          entryInfo(
            entry,
            when (entry) {
              is ProductScreen -> mapOf("productId" to entry.productId)
              is ProfileScreen -> mapOf("values" to List(999) { it })
              else -> emptyMap()
            },
          )
        }
      )

    val routes = sut.convert(listOf(first, middle, last), RetentionPolicy.KEEP_FIRST)

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry("/ProductScreen", mapOf("productId" to "sku-1")),
        NormalizedSentryBackStackEntry("/ProfileScreen"),
        NormalizedSentryBackStackEntry("/ProductScreen"),
      )
      .inOrder()
  }

  @Test
  fun `convert with KEEP_LAST treats entries as distinct by position even if structurally equal`() {
    val first = ProductScreen("sku-1")
    val middle = ProfileScreen("123")
    val last = ProductScreen("sku-1")
    val sut =
      getSut(
        entryMapper = { entry ->
          entryInfo(
            entry,
            when (entry) {
              is ProductScreen -> mapOf("productId" to entry.productId)
              is ProfileScreen -> mapOf("values" to List(999) { it })
              else -> emptyMap()
            },
          )
        }
      )

    val routes = sut.convert(listOf(first, middle, last), RetentionPolicy.KEEP_LAST)

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry("/ProductScreen"),
        NormalizedSentryBackStackEntry("/ProfileScreen"),
        NormalizedSentryBackStackEntry("/ProductScreen", mapOf("productId" to "sku-1")),
      )
      .inOrder()
  }

  @Test
  fun `convert trims mapper name`() {
    val sut = getSut(entryMapper = { SentryBackStackEntry(name = "  /profile ") })

    assertThat(sut.convert(ProfileScreen("123")))
      .isEqualTo(NormalizedSentryBackStackEntry(name = "/profile"))
  }

  @Test
  fun `convert adds leading slash to mapper name if absent`() {
    val sut = getSut(entryMapper = { SentryBackStackEntry(name = "profile") })

    assertThat(sut.convert(ProfileScreen("123")))
      .isEqualTo(NormalizedSentryBackStackEntry(name = "/profile"))
  }

  @Test
  fun `convert preserves leading slash in mapper name if already present`() {
    val sut = getSut(entryMapper = { SentryBackStackEntry(name = "/profile") })

    assertThat(sut.convert(ProfileScreen("123")))
      .isEqualTo(NormalizedSentryBackStackEntry(name = "/profile"))
  }

  @Test
  fun `convert returns unknown name if mapper name is blank`() {
    val sut = getSut(entryMapper = { SentryBackStackEntry("   ", mapOf("userId" to "123")) })

    assertThat(sut.convert(HomeScreen()))
      .isEqualTo(
        NormalizedSentryBackStackEntry(name = "/unknown", arguments = mapOf("userId" to "123"))
      )
    verify(logger)
      .log(
        eq(WARNING),
        eq(
          "Nav3 backStackEntryMapper returned a blank name while processing this back stack update. " +
            "Using /unknown instead."
        ),
      )
  }

  @Test
  fun `convert returns unknown name without arguments if mapper throws`() {
    val sut = getSut(entryMapper = { error("boom") })

    assertThat(sut.convert(HomeScreen()))
      .isEqualTo(NormalizedSentryBackStackEntry(UNKNOWN_ENTRY_NAME))
    verify(logger)
      .log(
        eq(WARNING),
        eq(
          "Nav3 backStackEntryMapper threw while resolving an entry. Using /unknown without arguments instead."
        ),
        org.mockito.kotlin.any<Throwable>(),
      )
  }

  @Test
  fun `convert returns arguments in proper serializable form`() {
    val text = StringBuilder("hello")
    val sut =
      getSut(
        entryMapper = { entry ->
          entryInfo(
            entry,
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
            ),
          )
        }
      )

    assertThat(sut.convert(HomeScreen()).arguments)
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
  fun `convert sanitizes arguments with nested supported containers recursively`() {
    val sut =
      getSut(
        entryMapper = { entry ->
          entryInfo(
            entry,
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
            ),
          )
        }
      )

    assertThat(sut.convert(HomeScreen()).arguments)
      .isEqualTo(
        mapOf(
          "nested" to
            mapOf("items" to listOf("x", listOf("y", "PRIVATE"), listOf(true, false), listOf("q")))
        )
      )
  }

  @Test
  fun `convert coerces arguments with unsupported values to strings`() {
    class OpaqueValue {
      override fun toString(): String = "opaque-value"
    }

    val sut = getSut(entryMapper = { entry -> entryInfo(entry, mapOf("bad" to OpaqueValue())) })

    assertThat(sut.convert(HomeScreen()).arguments).isEqualTo(mapOf("bad" to "opaque-value"))
  }

  @Test
  fun `convert logs an unsupported value warning once per back stack update`() {
    class OpaqueValue {
      override fun toString(): String = "opaque-value"
    }

    val sut =
      getSut(
        entryMapper = { entry ->
          entryInfo(
            entry,
            when (entry) {
              is HomeScreen -> mapOf("bad" to OpaqueValue())
              is ProfileScreen -> mapOf("alsoBad" to OpaqueValue())
              else -> emptyMap()
            },
          )
        }
      )

    sut.convert(listOf(HomeScreen(), ProfileScreen("123")), RetentionPolicy.KEEP_FIRST)

    verify(logger, times(1))
      .log(
        eq(WARNING),
        eq(
          "Nav3 backStackEntryMapper returned unsupported argument value of type %s while processing this " +
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

    val sut = getSut(entryMapper = { entry -> entryInfo(entry, mapOf("bad" to OpaqueValue())) })

    sut.convert(HomeScreen())
    clearInvocations(logger)

    sut.convert(HomeScreen())

    verify(logger, times(1))
      .log(
        eq(WARNING),
        eq(
          "Nav3 backStackEntryMapper returned unsupported argument value of type %s while processing this " +
            "back stack update. Falling back to toString(). Use String, CharSequence, Char, " +
            "Number, Boolean, Enum, Map, Collection, object Array, and primitive array values " +
            "for reliable results."
        ),
        eq("OpaqueValue"),
      )
  }

  @Test
  fun `convert returns empty arguments for cyclic structures`() {
    val cyclic = mutableMapOf<String, Any?>()
    cyclic["self"] = cyclic

    val sut = getSut(entryMapper = { entry -> entryInfo(entry, mapOf("cyclic" to cyclic)) })

    assertThat(sut.convert(HomeScreen()).arguments).isEmpty()
  }

  @Test
  fun `convert returns empty arguments for deeply nested structures`() {
    var nested: Any? = "value"
    repeat(25) { nested = listOf(nested) }

    val sut = getSut(entryMapper = { entry -> entryInfo(entry, mapOf("nested" to nested)) })

    assertThat(sut.convert(ProfileScreen("123")).arguments).isEmpty()
  }

  @Test
  fun `convert drops oversized arguments`() {
    val sut =
      getSut(entryMapper = { entry -> entryInfo(entry, mapOf("values" to List(1_001) { it })) })

    assertThat(sut.convert(HomeScreen()).arguments).isEmpty()
  }

  @Test
  fun `arguments do not use caller collection size for allocation`() {
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
    val sut = getSut(entryMapper = { entry -> entryInfo(entry, mapOf("values" to values)) })

    assertThat(sut.convert(HomeScreen()).arguments).isEqualTo(mapOf("values" to listOf(1, 2)))
    assertThat(values.wasSizeRead).isFalse()
  }

  @Test
  fun `omitted arguments normalize to an empty map`() {
    val sut = getSut()

    assertThat(sut.convert(HomeScreen()).arguments).isEmpty()
  }

  @Test
  fun `serialize returns entries in serialized form`() {
    val sut =
      getSut(
        entryMapper = { entry ->
          entryInfo(
            entry,
            when (entry) {
              is HomeScreen -> mapOf("tab" to entry.id)
              is ProfileScreen -> emptyMap()
              is SettingsScreen -> mapOf("section" to entry.section)
              else -> emptyMap()
            },
          )
        }
      )

    val routes =
      sut.convert(
        listOf(SettingsScreen("privacy"), ProfileScreen("123")),
        RetentionPolicy.KEEP_FIRST,
      )

    assertThat(routes.map(NormalizedSentryBackStackEntry::serialize))
      .containsExactly(
        mapOf("entry" to "/SettingsScreen", "arguments" to mapOf("section" to "privacy")),
        mapOf("entry" to "/ProfileScreen"),
      )
      .inOrder()
  }
}
