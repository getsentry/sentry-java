package io.sentry.compose.navigation3.internal.backstack

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.JsonSerializer
import io.sentry.SentryLevel.WARNING
import io.sentry.SentryOptions
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter.RetentionPolicy
import io.sentry.compose.navigation3.internal.contract.ArgumentDropReason
import io.sentry.compose.navigation3.internal.contract.UNKNOWN_ENTRY_NAME
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason
import java.io.StringWriter
import kotlin.test.assertFailsWith
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
      SentryBackStackEntry(entry::class.simpleName ?: "<unknown>")
    }

  @Test
  fun `convert() - returns normalized back stack entries`() {
    val converter =
      newConverter(
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
        },
      )

    val routes =
      converter.convert(
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
  fun `convert() - with KEEP_FIRST returns entries in back stack order`() {
    val converter = newConverter()

    val routes =
      converter.convert(
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
  fun `convert() - with KEEP_LAST returns entries in back stack order`() {
    val converter = newConverter()

    val routes =
      converter.convert(
        listOf(SettingsScreen("privacy"), ProfileScreen("123"), HomeScreen()),
        RetentionPolicy.KEEP_LAST,
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
  fun `convert() - returns an empty list if provided back stack is empty`() {
    val converter = newConverter()

    assertThat(converter.convert(emptyList(), RetentionPolicy.KEEP_FIRST)).isEmpty()
  }

  @Test
  fun `convert() - trims mapper name`() {
    val converter = newConverter(entryMapper = { SentryBackStackEntry(name = "  /profile ") })

    assertThat(converter.convert(ProfileScreen("123")))
      .isEqualTo(NormalizedSentryBackStackEntry(name = "/profile"))
  }

  @Test
  fun `convert() - adds leading slash to mapper name if absent`() {
    val converter = newConverter(entryMapper = { SentryBackStackEntry(name = "profile") })

    assertThat(converter.convert(ProfileScreen("123")))
      .isEqualTo(NormalizedSentryBackStackEntry(name = "/profile"))
  }

  @Test
  fun `convert() - preserves leading slash in mapper name if already present`() {
    val converter = newConverter(entryMapper = { SentryBackStackEntry(name = "/profile") })

    assertThat(converter.convert(ProfileScreen("123")))
      .isEqualTo(NormalizedSentryBackStackEntry(name = "/profile"))
  }

  @Test
  fun `convert() - returns a named entry without an unknown entry reason if mapper returns a name`() {
    val converter = newConverter()

    assertThat(converter.convert(HomeScreen()).unknownEntryReason).isNull()
  }

  @Test
  fun `convert() - returns unknown name with the blank name reason and arguments if mapper name is blank`() {
    val converter =
      newConverter(entryMapper = { SentryBackStackEntry("   ", mapOf("userId" to "123")) })

    assertThat(converter.convert(HomeScreen()))
      .isEqualTo(
        NormalizedSentryBackStackEntry(
          name = "/unknown",
          arguments = mapOf("userId" to "123"),
          unknownEntryReason = UnknownEntryReason.BLANK_NAME,
        ),
      )
    verify(logger)
      .log(
        eq(WARNING),
        eq(BLANK_NAME_WARNING),
      )
  }

  @Test
  fun `convert() - returns the reason arguments were dropped if mapper name is blank`() {
    val converter =
      newConverter(
        entryMapper = { SentryBackStackEntry("   ", mapOf("values" to List(999) { it })) },
      )

    assertThat(converter.convert(HomeScreen()))
      .isEqualTo(
        NormalizedSentryBackStackEntry(
          name = "/unknown",
          argumentDropReason = ArgumentDropReason.MAX_COUNT,
          unknownEntryReason = UnknownEntryReason.BLANK_NAME,
        ),
      )
  }

  @Test
  fun `convert() - logs the blank name warning once per back stack update`() {
    val converter = newConverter(entryMapper = { SentryBackStackEntry("   ") })

    converter.convert(listOf(HomeScreen(), ProfileScreen("123")), RetentionPolicy.KEEP_FIRST)

    verify(logger, times(1)).log(eq(WARNING), eq(BLANK_NAME_WARNING))
  }

  @Test
  fun `convert() - logs the blank name warning again in a later back stack update`() {
    val converter = newConverter(entryMapper = { SentryBackStackEntry("   ") })
    converter.convert(HomeScreen())
    clearInvocations(logger)

    converter.convert(HomeScreen())

    verify(logger, times(1)).log(eq(WARNING), eq(BLANK_NAME_WARNING))
  }

  @Test
  fun `convert() - returns unknown name with the unmapped reason if mapper returns null`() {
    val converter = newConverter(entryMapper = { null })

    assertThat(converter.convert(HomeScreen()))
      .isEqualTo(
        NormalizedSentryBackStackEntry(
          UNKNOWN_ENTRY_NAME,
          unknownEntryReason = UnknownEntryReason.UNMAPPED,
        ),
      )
  }

  @Test
  fun `convert() - returns unknown name with the mapping failed reason and no arguments if mapper throws`() {
    val converter = newConverter(entryMapper = { error("boom") })

    assertThat(converter.convert(HomeScreen()))
      .isEqualTo(
        NormalizedSentryBackStackEntry(
          UNKNOWN_ENTRY_NAME,
          unknownEntryReason = UnknownEntryReason.MAPPING_FAILED,
        ),
      )
    verify(logger)
      .log(
        eq(WARNING),
        eq(MAPPER_FAILURE_WARNING),
        any<Throwable>(),
      )
  }

  @Test
  fun `convert() - logs the mapper failure warning once per back stack update`() {
    val converter = newConverter(entryMapper = { error("boom") })

    converter.convert(listOf(HomeScreen(), ProfileScreen("123")), RetentionPolicy.KEEP_FIRST)

    verify(logger, times(1)).log(eq(WARNING), eq(MAPPER_FAILURE_WARNING), any<Throwable>())
  }

  @Test
  fun `convert() - logs the mapper failure warning again in a later back stack update`() {
    val converter = newConverter(entryMapper = { error("boom") })
    converter.convert(HomeScreen())
    clearInvocations(logger)

    converter.convert(HomeScreen())

    verify(logger, times(1)).log(eq(WARNING), eq(MAPPER_FAILURE_WARNING), any<Throwable>())
  }

  @Test
  fun `convert() - rethrows fatal errors thrown by the mapper`() {
    val converter = newConverter(entryMapper = { throw StackOverflowError("fatal") })

    assertFailsWith<StackOverflowError> { converter.convert(HomeScreen()) }
    verify(logger, never()).log(eq(WARNING), eq(MAPPER_FAILURE_WARNING), any<Throwable>())
  }

  @Test
  fun `convert() - returns the mapper's arguments sanitized`() {
    val converter =
      newConverter(
        entryMapper = { entry ->
          entryInfo(entry, mapOf("mode" to PrivacyMode.PRIVATE, "ids" to intArrayOf(1, 2)))
        },
      )

    assertThat(converter.convert(HomeScreen()).arguments)
      .isEqualTo(mapOf("mode" to "PRIVATE", "ids" to listOf(1, 2)))
  }

  @Test
  fun `convert() - returns empty arguments if mapper omits them`() {
    val converter = newConverter()

    assertThat(converter.convert(HomeScreen()).arguments).isEmpty()
  }

  @Test
  fun `convert() - with KEEP_FIRST preserves arguments nearest index zero when sanitization budget is exceeded`() {
    val first = SettingsScreen("privacy")
    val middle = ProfileScreen("123")
    val last = HomeScreen()
    val converter =
      newConverter(
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
        },
      )

    val routes = converter.convert(listOf(first, middle, last), RetentionPolicy.KEEP_FIRST)

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry("/SettingsScreen", mapOf("section" to "privacy")),
        NormalizedSentryBackStackEntry(
          "/ProfileScreen",
          argumentDropReason = ArgumentDropReason.MAX_COUNT,
        ),
        NormalizedSentryBackStackEntry(
          "/HomeScreen",
          argumentDropReason = ArgumentDropReason.MAX_COUNT,
        ),
      )
      .inOrder()
  }

  @Test
  fun `convert() - with KEEP_LAST preserves arguments nearest lastIndex when sanitization budget is exceeded`() {
    val first = HomeScreen()
    val middle = ProfileScreen("123")
    val last = SettingsScreen("privacy")
    val converter =
      newConverter(
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
        },
      )

    val routes = converter.convert(listOf(first, middle, last), RetentionPolicy.KEEP_LAST)

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry(
          "/HomeScreen",
          argumentDropReason = ArgumentDropReason.MAX_COUNT,
        ),
        NormalizedSentryBackStackEntry(
          "/ProfileScreen",
          argumentDropReason = ArgumentDropReason.MAX_COUNT,
        ),
        NormalizedSentryBackStackEntry("/SettingsScreen", mapOf("section" to "privacy")),
      )
      .inOrder()
  }

  @Test
  fun `convert() - with KEEP_FIRST treats entries as distinct by position even if structurally equal`() {
    val first = ProductScreen("sku-1")
    val middle = ProfileScreen("123")
    val last = ProductScreen("sku-1")
    val converter =
      newConverter(
        entryMapper = { entry ->
          entryInfo(
            entry,
            when (entry) {
              is ProductScreen -> mapOf("productId" to entry.productId)
              is ProfileScreen -> mapOf("values" to List(999) { it })
              else -> emptyMap()
            },
          )
        },
      )

    val routes = converter.convert(listOf(first, middle, last), RetentionPolicy.KEEP_FIRST)

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry("/ProductScreen", mapOf("productId" to "sku-1")),
        NormalizedSentryBackStackEntry(
          "/ProfileScreen",
          argumentDropReason = ArgumentDropReason.MAX_COUNT,
        ),
        NormalizedSentryBackStackEntry(
          "/ProductScreen",
          argumentDropReason = ArgumentDropReason.MAX_COUNT,
        ),
      )
      .inOrder()
  }

  @Test
  fun `convert() - with KEEP_LAST treats entries as distinct by position even if structurally equal`() {
    val first = ProductScreen("sku-1")
    val middle = ProfileScreen("123")
    val last = ProductScreen("sku-1")
    val converter =
      newConverter(
        entryMapper = { entry ->
          entryInfo(
            entry,
            when (entry) {
              is ProductScreen -> mapOf("productId" to entry.productId)
              is ProfileScreen -> mapOf("values" to List(999) { it })
              else -> emptyMap()
            },
          )
        },
      )

    val routes = converter.convert(listOf(first, middle, last), RetentionPolicy.KEEP_LAST)

    assertThat(routes)
      .containsExactly(
        NormalizedSentryBackStackEntry(
          "/ProductScreen",
          argumentDropReason = ArgumentDropReason.MAX_COUNT,
        ),
        NormalizedSentryBackStackEntry(
          "/ProfileScreen",
          argumentDropReason = ArgumentDropReason.MAX_COUNT,
        ),
        NormalizedSentryBackStackEntry("/ProductScreen", mapOf("productId" to "sku-1")),
      )
      .inOrder()
  }

  @Test
  fun `convert() - starts each back stack update with a fresh argument budget`() {
    val converter =
      newConverter(entryMapper = { entry -> entryInfo(entry, mapOf("values" to List(150) { it })) })

    converter.convert(HomeScreen())
    val entry = converter.convert(HomeScreen())

    assertThat(entry.arguments).isEqualTo(mapOf("values" to List(150) { it }))
    assertThat(entry.argumentDropReason).isNull()
  }

  @Test
  fun `convert() - hides state reads during argument sanitization from snapshot observation`() {
    val arguments = mutableStateMapOf<String, Any?>("state" to "value")
    val converter = newConverter(entryMapper = { entry -> entryInfo(entry, arguments) })
    val observedReads = mutableListOf<Any>()
    val snapshot = Snapshot.takeSnapshot(readObserver = { observedReads += it })

    val converted =
      try {
        snapshot.enter { converter.convert(HomeScreen()) }
      } finally {
        snapshot.dispose()
      }

    assertThat(converted.arguments).isEqualTo(mapOf("state" to "value"))
    assertThat(observedReads).isEmpty()
  }

  @Test
  fun `convert() - logs the unsupported value warning again in a later back stack update`() {
    class OpaqueValue {
      override fun toString(): String = "opaque-value"
    }

    val converter =
      newConverter(entryMapper = { entry -> entryInfo(entry, mapOf("bad" to OpaqueValue())) })

    converter.convert(HomeScreen())
    clearInvocations(logger)

    converter.convert(HomeScreen())

    verify(logger, times(1))
      .log(
        eq(WARNING),
        eq(UNSUPPORTED_VALUE_WARNING),
        eq("OpaqueValue"),
      )
  }

  @Test
  fun `serialization integration - non-finite number arguments serialize to valid JSON through JsonSerializer`() {
    val converter =
      newConverter(
        entryMapper = { entry ->
          entryInfo(
            entry,
            mapOf(
              "price" to Double.NaN,
              "limits" to floatArrayOf(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY),
            ),
          )
        },
      )
    val serializedEntry = converter.convert(HomeScreen()).serialize()
    val json = StringWriter()

    JsonSerializer(SentryOptions()).serialize(serializedEntry, json)

    assertThat(json.toString())
      .isEqualTo(
        """{"entry":"/HomeScreen","entry_arguments":{"price":"NaN","limits":["Infinity","-Infinity"]}}""",
      )
  }

  private fun newConverter(
    entryMapper: BackStackEntryMapper<Any> = defaultEntryMapper,
  ): BackStackConverter<Any> =
    BackStackConverter(
      entryMapper = ForwardingBackStackEntryMapper { entryMapper },
      logger = logger,
    )

  private fun entryInfo(entry: Any, arguments: Map<String, Any?>? = null): SentryBackStackEntry =
    SentryBackStackEntry(entry::class.simpleName ?: "<unknown>", arguments)

  private fun BackStackConverter<Any>.convert(entry: Any): NormalizedSentryBackStackEntry =
    convert(listOf(entry), RetentionPolicy.KEEP_FIRST).single()

  private companion object {
    const val BLANK_NAME_WARNING =
      "Nav3 backStackEntryMapper returned a blank name. Using /unknown."
    const val MAPPER_FAILURE_WARNING =
      "Nav3 backStackEntryMapper threw. Using /unknown without arguments."
    const val UNSUPPORTED_VALUE_WARNING =
      "Nav3 argument type %s is unsupported. Using toString(). Return scalars, enums, maps, " +
        "collections, or arrays from BackStackEntryMapper."
  }
}
