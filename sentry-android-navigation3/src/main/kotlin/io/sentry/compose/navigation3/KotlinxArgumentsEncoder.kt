@file:OptIn(ExperimentalSerializationApi::class)

package io.sentry.compose.navigation3

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Encodes a `@Serializable` back stack key into plain maps, lists and scalars, suitable as
 * [SentryBackStackEntry.arguments].
 *
 * kotlinx-serialization-core has no output format of its own, and Nav3 apps don't necessarily
 * include kotlinx-serialization-json, so this is a minimal tree encoder:
 * - classes and objects become maps from property name to value;
 * - lists and sets become lists; maps become maps with stringified keys;
 * - enums become their serial names; value classes become their underlying value;
 * - polymorphic values become `{"type": <serial name>, "value": <value>}`.
 *
 * Only references kotlinx.serialization, so it must only be used once the library is known to be
 * present (see [SerialNameBackStackEntryMapper]).
 */
internal object KotlinxArgumentsEncoder {

  /** Key used when the top-level value doesn't encode to a map (e.g. custom serializers). */
  const val VALUE_KEY: String = "value"

  /**
   * Encodes [value] with [serializer].
   *
   * @throws kotlinx.serialization.SerializationException if a value can't be encoded, e.g. a
   *   contextual or open polymorphic property with no registered serializer.
   */
  fun encode(serializer: KSerializer<Any>, value: Any): Map<String, Any?> {
    var result: Any? = null
    ValueEncoder { result = it }.encodeSerializableValue(serializer, value)

    @Suppress("UNCHECKED_CAST")
    return result as? Map<String, Any?> ?: mapOf(VALUE_KEY to result)
  }

  /** Encodes a single value and hands it to [sink]. Structures are delegated to child encoders. */
  private open class ValueEncoder(private val sink: (Any?) -> Unit) : AbstractEncoder() {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    protected open fun put(value: Any?) {
      sink(value)
    }

    override fun encodeValue(value: Any) {
      put(value)
    }

    override fun encodeNull() {
      put(null)
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
      put(enumDescriptor.getElementName(index))
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
      when (descriptor.kind) {
        StructureKind.LIST -> ListEncoder(::put)
        StructureKind.MAP -> MapEncoder(::put)
        else -> ClassEncoder(::put)
      }
  }

  /** Collects the elements of a class, object, or polymorphic value into a map. */
  private class ClassEncoder(private val parentSink: (Any?) -> Unit) : ValueEncoder(parentSink) {
    private val values = LinkedHashMap<String, Any?>()
    private var currentKey = ""

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
      currentKey = descriptor.getElementName(index)
      return true
    }

    override fun put(value: Any?) {
      values[currentKey] = value
    }

    override fun endStructure(descriptor: SerialDescriptor) {
      parentSink(values)
    }
  }

  /** Collects the elements of a list or set. */
  private class ListEncoder(private val parentSink: (Any?) -> Unit) : ValueEncoder(parentSink) {
    private val values = ArrayList<Any?>()

    override fun put(value: Any?) {
      values += value
    }

    override fun endStructure(descriptor: SerialDescriptor) {
      parentSink(values)
    }
  }

  /** Collects the entries of a map. Elements alternate between keys and values. */
  private class MapEncoder(private val parentSink: (Any?) -> Unit) : ValueEncoder(parentSink) {
    private val values = LinkedHashMap<String, Any?>()
    private var isKey = true
    private var currentKey = ""

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
      isKey = index % 2 == 0
      return true
    }

    override fun put(value: Any?) {
      if (isKey) {
        currentKey = value.toString()
      } else {
        values[currentKey] = value
      }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
      parentSink(values)
    }
  }
}
