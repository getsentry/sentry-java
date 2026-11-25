package io.sentry.compose.navigation3.internal.backstack

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.JsonSerializer
import io.sentry.SentryLevel.WARNING
import io.sentry.SentryOptions
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter.RetentionPolicy
import io.sentry.compose.navigation3.internal.backstack.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME
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

  private companion object {
    const val BLANK_NAME_WARNING =
      "Nav3 backStackEntryMapper returned a blank name. Using /unknown."
    const val MAPPER_FAILURE_WARNING =
      "Nav3 backStackEntryMapper threw. Using /unknown without arguments."
    const val UNSUPPORTED_VALUE_WARNING =
      "Nav3 argument type %s is unsupported. Using toString(). Return scalars, enums, maps, " +
        "collections, or arrays from BackStackEntryMapper."
  }

  private val defaultEntryMapper =
    BackStackEntryMapper<Any> { entry ->
      SentryBackStackEntry(entry::class.simpleName ?: "<unknown>")
    }

  /** Reports a reason and returns `null`, like a built-in mapper that can't name an entry. */
  private val notSerializableMapper =
    BackStackEntryMapper<Any> {
      UnknownEntryReasonReports.report(UnknownEntryReason.NOT_SERIALIZABLE)
      null
    }

  private fun getSut(
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
        },
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
  fun `convert with KEEP_FIRST returns entries in back stack order`() {
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
  fun `convert with KEEP_LAST returns entries in back stack order`() {
    val sut = getSut()

    val routes =
      sut.convert(
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
        },
      )

    val routes = sut.convert(listOf(first, middle, last), RetentionPolicy.KEEP_FIRST)

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
        },
      )

    val routes = sut.convert(listOf(first, middle, last), RetentionPolicy.KEEP_LAST)

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
        },
      )

    val routes = sut.convert(listOf(first, middle, last), RetentionPolicy.KEEP_FIRST)

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
        },
      )

    val routes = sut.convert(listOf(first, middle, last), RetentionPolicy.KEEP_LAST)

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
  fun `convert returns a named entry without an unnamed reason if mapper returns a name`() {
    val sut = getSut()

    assertThat(sut.convert(HomeScreen()).unknownEntryReason).isNull()
  }

  @Test
  fun `convert returns unknown name with the blank name reason and arguments if mapper name is blank`() {
    val sut = getSut(entryMapper = { SentryBackStackEntry("   ", mapOf("userId" to "123")) })

    assertThat(sut.convert(HomeScreen()))
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
  fun `convert logs the blank name warning once per back stack update`() {
    val sut = getSut(entryMapper = { SentryBackStackEntry("   ") })

    sut.convert(listOf(HomeScreen(), ProfileScreen("123")), RetentionPolicy.KEEP_FIRST)

    verify(logger, times(1)).log(eq(WARNING), eq(BLANK_NAME_WARNING))
  }

  @Test
  fun `convert returns unknown name with the unmapped reason if mapper returns null without a report`() {
    val sut = getSut(entryMapper = { null })

    assertThat(sut.convert(HomeScreen()))
      .isEqualTo(
        NormalizedSentryBackStackEntry(
          UNKNOWN_ENTRY_NAME,
          unknownEntryReason = UnknownEntryReason.UNMAPPED,
        ),
      )
  }

  @Test
  fun `convert returns unknown name with the reported reason if mapper reports one and returns null`() {
    val sut = getSut(entryMapper = notSerializableMapper)

    assertThat(sut.convert(HomeScreen()))
      .isEqualTo(
        NormalizedSentryBackStackEntry(
          UNKNOWN_ENTRY_NAME,
          unknownEntryReason = UnknownEntryReason.NOT_SERIALIZABLE,
        ),
      )
  }

  @Test
  fun `convert returns the reported reason if a custom mapper forwards a reporting mapper's null`() {
    val sut = getSut(entryMapper = { entry -> notSerializableMapper.map(entry) })

    assertThat(sut.convert(HomeScreen()).unknownEntryReason)
      .isEqualTo(UnknownEntryReason.NOT_SERIALIZABLE)
  }

  @Test
  fun `convert returns the built-in mapper's reason if a custom mapper forwards SerialName's null`() {
    val sut = getSut(entryMapper = { entry -> BackStackEntryMapper.SerialName.map(entry) })

    assertThat(sut.convert(HomeScreen()))
      .isEqualTo(
        NormalizedSentryBackStackEntry(
          UNKNOWN_ENTRY_NAME,
          unknownEntryReason = UnknownEntryReason.NOT_SERIALIZABLE,
        ),
      )
  }

  @Test
  fun `convert returns a named entry if a custom mapper names an entry after a reporting mapper's null`() {
    val sut =
      getSut(
        entryMapper = { entry ->
          notSerializableMapper.map(entry) ?: SentryBackStackEntry("fallback")
        },
      )

    assertThat(sut.convert(HomeScreen()))
      .isEqualTo(NormalizedSentryBackStackEntry(name = "/fallback"))
  }

  @Test
  fun `convert returns the unmapped reason if a custom mapper returns null after another mapper names the entry`() {
    val sut =
      getSut(
        entryMapper = { entry ->
          defaultEntryMapper.map(entry)
          null
        },
      )

    assertThat(sut.convert(HomeScreen()).unknownEntryReason).isEqualTo(UnknownEntryReason.UNMAPPED)
  }

  @Test
  fun `convert does not carry a reported reason over to the next back stack entry`() {
    val sut =
      getSut(
        entryMapper = { entry ->
          when (entry) {
            is HomeScreen -> notSerializableMapper.map(entry)
            else -> null
          }
        },
      )

    val routes = sut.convert(listOf(HomeScreen(), ProfileScreen("123")), RetentionPolicy.KEEP_FIRST)

    assertThat(routes.map { it.unknownEntryReason })
      .containsExactly(UnknownEntryReason.NOT_SERIALIZABLE, UnknownEntryReason.UNMAPPED)
      .inOrder()
  }

  @Test
  fun `convert returns unknown name with the mapping failed reason and no arguments if mapper throws`() {
    val sut = getSut(entryMapper = { error("boom") })

    assertThat(sut.convert(HomeScreen()))
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
  fun `convert returns the mapping failed reason if mapper throws after reporting a reason`() {
    val sut =
      getSut(
        entryMapper = { entry ->
          notSerializableMapper.map(entry)
          error("boom")
        },
      )

    assertThat(sut.convert(HomeScreen()).unknownEntryReason)
      .isEqualTo(UnknownEntryReason.MAPPING_FAILED)
  }

  @Test
  fun `convert logs the mapper failure warning once per back stack update`() {
    val sut = getSut(entryMapper = { error("boom") })

    sut.convert(listOf(HomeScreen(), ProfileScreen("123")), RetentionPolicy.KEEP_FIRST)

    verify(logger, times(1)).log(eq(WARNING), eq(MAPPER_FAILURE_WARNING), any<Throwable>())
  }

  @Test
  fun `convert rethrows fatal errors thrown by the mapper`() {
    val sut = getSut(entryMapper = { throw StackOverflowError("fatal") })

    assertFailsWith<StackOverflowError> { sut.convert(HomeScreen()) }
    verify(logger, never()).log(eq(WARNING), eq(MAPPER_FAILURE_WARNING), any<Throwable>())
  }

  @Test
  fun `convert returns the mapper's arguments sanitized`() {
    val sut =
      getSut(
        entryMapper = { entry ->
          entryInfo(entry, mapOf("mode" to PrivacyMode.PRIVATE, "ids" to intArrayOf(1, 2)))
        },
      )

    assertThat(sut.convert(HomeScreen()).arguments)
      .isEqualTo(mapOf("mode" to "PRIVATE", "ids" to listOf(1, 2)))
  }

  @Test
  fun `convert drops the arguments for the character limit without converting mapper text that's too long`() {
    val text = CountingText(length = MAX_ARGUMENT_CHARACTERS + 1, text = "short")
    val sut = getSut(entryMapper = { entry -> entryInfo(entry, mapOf("title" to text)) })

    val route = sut.convert(HomeScreen())

    assertThat(route)
      .isEqualTo(
        NormalizedSentryBackStackEntry(
          "/HomeScreen",
          argumentDropReason = ArgumentDropReason.CHARACTER_LIMIT,
        ),
      )
    assertThat(text.toStringCalls).isEqualTo(0)
  }

  @Test
  fun `convert hides state reads during argument sanitization from snapshot observation`() {
    val arguments = mutableStateMapOf<String, Any?>("state" to "value")
    val sut = getSut(entryMapper = { entry -> entryInfo(entry, arguments) })
    val observedReads = mutableListOf<Any>()
    val snapshot = Snapshot.takeSnapshot(readObserver = { observedReads += it })

    val converted =
      try {
        snapshot.enter { sut.convert(HomeScreen()) }
      } finally {
        snapshot.dispose()
      }

    assertThat(converted.arguments).isEqualTo(mapOf("state" to "value"))
    assertThat(observedReads).isEmpty()
  }

  @Test
  fun `convert hides state reads by an ArgumentSource from snapshot observation`() {
    val state = mutableStateOf("value")
    val arguments = SelfWritingArguments { sink ->
      sink.key("state")
      sink.scalar(state.value)
    }
    val sut = getSut(entryMapper = { entry -> entryInfo(entry, arguments) })
    val observedReads = mutableListOf<Any>()
    val snapshot = Snapshot.takeSnapshot(readObserver = { observedReads += it })

    val converted =
      try {
        snapshot.enter { sut.convert(HomeScreen()) }
      } finally {
        snapshot.dispose()
      }

    assertThat(converted.arguments).isEqualTo(mapOf("state" to "value"))
    assertThat(observedReads).isEmpty()
  }

  @Test
  fun `convert logs the unsupported value warning again in a later back stack update`() {
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
        eq(UNSUPPORTED_VALUE_WARNING),
        eq("OpaqueValue"),
      )
  }

  @Test
  fun `convert starts each back stack update with a fresh argument budget`() {
    val sut =
      getSut(entryMapper = { entry -> entryInfo(entry, mapOf("values" to List(150) { it })) })

    sut.convert(HomeScreen())
    val entry = sut.convert(HomeScreen())

    assertThat(entry.arguments).isEqualTo(mapOf("values" to List(150) { it }))
    assertThat(entry.argumentDropReason).isNull()
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
        },
      )

    val routes =
      sut.convert(
        listOf(SettingsScreen("privacy"), ProfileScreen("123")),
        RetentionPolicy.KEEP_FIRST,
      )

    assertThat(routes.map(NormalizedSentryBackStackEntry::serialize))
      .containsExactly(
        mapOf("entry" to "/SettingsScreen", "entry_arguments" to mapOf("section" to "privacy")),
        mapOf("entry" to "/ProfileScreen"),
      )
      .inOrder()
  }

  @Test
  fun `converted entries with non-finite number arguments serialize to valid JSON through Sentry's JSON serializer`() {
    val sut =
      getSut(
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
    val serializedEntry = sut.convert(HomeScreen()).serialize()
    val json = StringWriter()

    JsonSerializer(SentryOptions()).serialize(serializedEntry, json)

    assertThat(json.toString())
      .isEqualTo(
        """{"entry":"/HomeScreen","entry_arguments":{"price":"NaN","limits":["Infinity","-Infinity"]}}""",
      )
  }
}
