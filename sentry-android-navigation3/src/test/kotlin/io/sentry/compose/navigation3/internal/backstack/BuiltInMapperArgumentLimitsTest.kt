package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter.RetentionPolicy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Converts back stacks with [BackStackEntryMapper.SerialNameAndProperties] whose entries' host app
 * serializers interfere with how the argument limits are enforced, checking the drop reasons
 * recorded across the kotlinx encoding and argument sanitization layers.
 *
 * Each back stack is ordered top first, as [BackStackObserver] converts it.
 */
class BuiltInMapperArgumentLimitsTest {

  @Serializable(with = ReportSerializer::class) private class ReportRoute(val body: String)

  /**
   * Encodes a [ReportRoute] as a class with its body, but replaces any exception with a
   * [SerializationException] of its own, without the original as the cause, as a host app
   * serializer might.
   */
  private object ReportSerializer : KSerializer<ReportRoute> {
    override val descriptor: SerialDescriptor =
      buildClassSerialDescriptor("Report") { element<String>("body") }

    override fun serialize(encoder: Encoder, value: ReportRoute) {
      try {
        encoder.encodeStructure(descriptor) { encodeStringElement(descriptor, 0, value.body) }
      } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
        throw SerializationException("Couldn't encode the report")
      }
    }

    override fun deserialize(decoder: Decoder): ReportRoute = throw UnsupportedOperationException()
  }

  @Serializable(with = DraftSerializer::class) private class DraftRoute(val lineIds: List<Int>)

  /**
   * Encodes a [DraftRoute] as a class with its line ids, then throws, as a host app serializer that
   * fails after writing part of an entry's properties might.
   */
  private object DraftSerializer : KSerializer<DraftRoute> {
    override val descriptor: SerialDescriptor =
      buildClassSerialDescriptor("Draft") { element<List<Int>>("lineIds") }

    override fun serialize(encoder: Encoder, value: DraftRoute) {
      encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, ListSerializer(Int.serializer()), value.lineIds)
      }
      throw SerializationException("Couldn't encode the draft")
    }

    override fun deserialize(decoder: Decoder): DraftRoute = throw UnsupportedOperationException()
  }

  @Serializable @SerialName("Catalog") private data class CatalogRoute(val itemIds: List<Int>)

  @Serializable @SerialName("Cart") private data class CartRoute(val cartId: String)

  /** Wraps the built-in mapper and reads each entry's arguments before returning them unchanged. */
  private val argumentReadingMapper =
    BackStackEntryMapper<Any> { entry ->
      BackStackEntryMapper.SerialNameAndProperties.map(entry)?.also { it.arguments?.size }
    }

  private val logger = mock<ILogger>()

  @Test
  fun `an entry whose serializer replaces the character limit failure drops its own and older entries' arguments`() {
    // The body alone is one character over the limit of MAX_ARGUMENT_CHARACTERS per update.
    val report = ReportRoute(body = "b".repeat(MAX_ARGUMENT_CHARACTERS + 1))
    val backStack = listOf(report, CartRoute(cartId = "c-1"))

    val converted =
      BackStackConverter(
          ForwardingBackStackEntryMapper { BackStackEntryMapper.SerialNameAndProperties },
          logger,
        )
        .convert(backStack, RetentionPolicy.KEEP_FIRST)

    assertThat(converted)
      .containsExactly(
        NormalizedSentryBackStackEntry(
          "/Report",
          argumentDropReason = ArgumentDropReason.CHARACTER_LIMIT,
        ),
        NormalizedSentryBackStackEntry(
          "/Cart",
          argumentDropReason = ArgumentDropReason.CHARACTER_LIMIT,
        ),
      )
      .inOrder()
  }

  @Test
  fun `a serializer throwing after overflowing the value budget drops older entries' arguments even if read first`() {
    // The catalog spends 151 of the MAX_ARGUMENT_VALUES = 200 values per update: its list and 150
    // items. The draft's list and 59 items are 60 more, which fit in a budget of their own but
    // overflow what the catalog left, before the draft's serializer throws.
    val catalog = CatalogRoute(itemIds = List(150) { it })
    val draft = DraftRoute(lineIds = List(59) { it })
    val backStack = listOf(catalog, draft, CartRoute(cartId = "c-1"))

    val written = convert(backStack, BackStackEntryMapper.SerialNameAndProperties)
    val readFirst = convert(backStack, argumentReadingMapper)

    assertThat(readFirst).isEqualTo(written)
    assertThat(written)
      .containsExactly(
        NormalizedSentryBackStackEntry("/Catalog", mapOf("itemIds" to List(150) { it })),
        NormalizedSentryBackStackEntry("/Draft", argumentDropReason = ArgumentDropReason.MAX_COUNT),
        NormalizedSentryBackStackEntry("/Cart", argumentDropReason = ArgumentDropReason.MAX_COUNT),
      )
      .inOrder()
  }

  private fun convert(
    backStack: List<Any>,
    entryMapper: BackStackEntryMapper<Any>,
  ): List<NormalizedSentryBackStackEntry> =
    BackStackConverter(ForwardingBackStackEntryMapper { entryMapper }, logger)
      .convert(backStack, RetentionPolicy.KEEP_FIRST)
}
