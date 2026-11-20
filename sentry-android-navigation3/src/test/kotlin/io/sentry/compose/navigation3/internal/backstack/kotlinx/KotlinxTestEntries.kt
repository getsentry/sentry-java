@file:Suppress("MatchingDeclarationName")

package io.sentry.compose.navigation3.internal.backstack.kotlinx

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable @SerialName("Home") internal data object Home

@Serializable @SerialName("Detail") internal data class Detail(val id: String)

@Serializable @SerialName("Search") internal data class Search(val query: String)

internal class Unregistered(val id: String)

@Serializable
@SerialName("WithContextual")
internal data class WithContextual(val id: String, @Contextual val other: Unregistered)

/** A serializer for tests that only read its descriptor, e.g., to name an entry. */
internal abstract class NamingOnlySerializer<T> : KSerializer<T> {
  override fun serialize(encoder: Encoder, value: T) = throw UnsupportedOperationException()

  override fun deserialize(decoder: Decoder): T = throw UnsupportedOperationException()
}

@Serializable internal data class Box<T>(val value: T)

/** Wraps [value] in [depth] nested [Box]es, so that [value] sits at [depth]. */
internal fun <T> boxed(
  depth: Int,
  value: T,
  serializer: KSerializer<T>,
): Pair<KSerializer<Any>, Any> {
  var boxedSerializer: KSerializer<*> = serializer
  var boxedValue: Any? = value
  repeat(depth) {
    @Suppress("UNCHECKED_CAST")
    boxedSerializer = Box.serializer(boxedSerializer as KSerializer<Any?>)
    boxedValue = Box(boxedValue)
  }
  @Suppress("UNCHECKED_CAST")
  return boxedSerializer as KSerializer<Any> to boxedValue!!
}

/** The arguments the encoder produces for [boxed] when within its limits. */
internal fun boxedArguments(depth: Int, value: Any?): Map<String, Any?> {
  var arguments: Any? = value
  repeat(depth) { arguments = mapOf("value" to arguments) }
  @Suppress("UNCHECKED_CAST")
  return arguments as Map<String, Any?>
}

internal class Link(val id: String) {
  var next: Link? = null
}

/**
 * Encodes a link as the id of the last link in its chain, encoding each next link in place of its
 * own, so the recursion never begins a structure.
 */
internal object LinkSerializer : KSerializer<Link> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("test.Link", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Link) {
    val next = value.next
    if (next != null) encoder.encodeSerializableValue(this, next)
    else encoder.encodeString(value.id)
  }

  override fun deserialize(decoder: Decoder): Link = Link(decoder.decodeString())
}

/** Chains [length] [Link]s, the last of which has the id `leaf`. */
internal fun linked(length: Int): Link {
  var link = Link("leaf")
  repeat(length - 1) { index -> link = Link("link-$index").apply { next = link } }
  return link
}
