@file:OptIn(ExperimentalSerializationApi::class)

package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.ArgumentSink
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.util.HttpUtils
import io.sentry.util.UrlUtils
import java.math.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Encodes a `@Serializable` back stack entry's properties as maps, lists, and scalars for use as
 * [SentryBackStackEntry.arguments].
 *
 * **Encoding formats**
 *
 * Uses a custom tree encoder because kotlinx-serialization-core provides no output format and Nav3
 * apps may not include kotlinx-serialization-json.
 *
 * - Classes and objects become property maps. Objects without properties record no arguments.
 *
 * - Lists and sets become lists. Maps use stringified scalar keys, such as strings, numbers, or
 *   enums. Structured map keys are rejected, matching kotlinx-serialization-json's defaults.
 *
 * - Enums use their serial names. Value classes use their underlying values.
 *
 * - Unsigned numbers (`UByte`, `UShort`, `UInt`, `ULong`) retain their unsigned values.
 *
 * - Polymorphic properties become `{"type": <serial name>, "value": <value>}`.
 *
 * - Entries encoded as scalars, such as by a custom string serializer, become `{"value": <value>}`.
 *
 * - Properties marked `@Transient` are omitted.
 *
 * **Argument limits**
 *
 * Writes through [ArgumentSink], which enforces the same size and structural limits as for custom
 * mappers. Violations throw [DropArguments], bounding traversal and allocation for cyclic, deeply
 * nested, or large entries.
 *
 * Custom serializers can recurse without nesting properties, bypassing the sink's depth limit. The
 * encoder separately limits nested serializer calls to [MAX_SERIALIZER_NESTING].
 *
 * **Sensitive data**
 *
 * Values under property names or map keys matching Sentry's
 * [sensitive denylist](https://develop.sentry.dev/sdk/foundations/client/data-collection/#sensitive-denylist)
 * are replaced with [UrlUtils.SENSITIVE_DATA_SUBSTITUTE] without invoking their serializers.
 *
 * [ValueEncoder.encodeInline] also checks value classes' underlying property names, which aren't
 * written as keys. [SensitiveKeyFilter] checks every output key as a safeguard against missed
 * encoder checks.
 *
 * The denylist originates from filtering HTTP headers, cookies, and query parameters. This encoder
 * applies it to navigation arguments as a baseline for sensitive-data filtering.
 */
internal object KotlinxPropertiesEncoder {

  /** Key for top-level values encoded without a map, such as by a custom serializer. */
  const val VALUE_KEY: String = "value"

  /**
   * Encodes [value] with [serializer], writing its top-level properties to [sink]'s root map.
   *
   * Throws [SerializationException] when a value cannot be encoded, such as when a contextual or
   * open polymorphic property has no registered serializer. Throws [DropArguments] when an argument
   * limit is exceeded.
   *
   * Limit failures, including [MAX_SERIALIZER_NESTING], are recorded in [ArgumentSink.failure]. A
   * serializer can catch [DropArguments] and then throw a different exception or return normally.
   * Callers must check [ArgumentSink.failure] to determine whether to drop the arguments.
   */
  fun encode(serializer: KSerializer<Any>, value: Any, sink: ArgumentSink) {
    val filteredSink = SensitiveKeyFilter(sink)
    RootEncoder(filteredSink, SerializerNesting(filteredSink))
      .encodeSerializableValue(serializer, value)
  }

  /** Encodes a single value and writes it to [sink]. Structures are delegated to child encoders. */
  private open class ValueEncoder(
    protected val sink: ArgumentSink,
    protected val nesting: SerializerNesting,
  ) : AbstractEncoder() {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    protected open fun put(value: Any?) {
      sink.scalar(value)
    }

    /** Replaces a filtered value with [UrlUtils.SENSITIVE_DATA_SUBSTITUTE]. */
    protected open fun putFiltered() {
      put(UrlUtils.SENSITIVE_DATA_SUBSTITUTE)
    }

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
      nesting.enter()
      try {
        serializer.serialize(this, value)
      } finally {
        nesting.exit()
      }
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

    override fun encodeInline(descriptor: SerialDescriptor): Encoder =
      when {
        descriptor.serialName in UNSIGNED_SERIAL_NAMES -> UnsignedEncoder(::put)
        // A value class records only its underlying value, so its underlying property's name
        // never reaches encodeElement. We check it here instead.
        descriptor.hasSensitiveUnderlyingProperty() -> {
          putFiltered()
          DiscardingEncoder
        }
        else -> this
      }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
      // The sink checks the depth as the structure begins, so a cyclic or deeply nested entry
      // stops here instead of recursing until the stack overflows.
      when (descriptor.kind) {
        StructureKind.LIST -> {
          sink.beginList()
          ListEncoder(sink, nesting)
        }
        StructureKind.MAP -> {
          sink.beginMap()
          MapEncoder(sink, nesting)
        }
        else -> {
          sink.beginMap()
          ClassEncoder(sink, nesting)
        }
      }
  }

  /**
   * Encodes the entry itself. A class's properties, or a map's entries, are written to the sink's
   * root map. Anything else (e.g., a custom serializer that encodes to a string) is written under
   * [VALUE_KEY].
   */
  private class RootEncoder(sink: ArgumentSink, nesting: SerializerNesting) :
    ValueEncoder(sink, nesting) {

    override fun put(value: Any?) {
      sink.key(VALUE_KEY)
      sink.scalar(value)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
      when (descriptor.kind) {
        StructureKind.LIST -> {
          sink.key(VALUE_KEY)
          super.beginStructure(descriptor)
        }
        StructureKind.MAP -> MapEncoder(sink, nesting, isRoot = true)
        else -> ClassEncoder(sink, nesting, isRoot = true)
      }
  }

  /**
   * Writes the elements of a class, object, or polymorphic value as a map.
   *
   * If [isRoot] is true, uses the sink's already-open root map.
   */
  private class ClassEncoder(
    sink: ArgumentSink,
    nesting: SerializerNesting,
    private val isRoot: Boolean = false,
  ) : ValueEncoder(sink, nesting) {

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
      val name = descriptor.getElementName(index)
      sink.key(name)
      if (isSensitive(name)) {
        // Skipping the element keeps a sensitive value's own serializers from running.
        put(UrlUtils.SENSITIVE_DATA_SUBSTITUTE)
        return false
      }
      return true
    }

    override fun endStructure(descriptor: SerialDescriptor) {
      if (!isRoot) {
        sink.end()
      }
    }
  }

  /** Writes the elements of a list or set. */
  private class ListEncoder(sink: ArgumentSink, nesting: SerializerNesting) :
    ValueEncoder(sink, nesting) {

    override fun endStructure(descriptor: SerialDescriptor) {
      sink.end()
    }
  }

  /**
   * Writes alternating map keys and values. If [isRoot] is true, uses the sink's already-open root
   * map.
   *
   * Keys must be scalars and are recorded as strings, matching kotlinx-serialization-json's
   * defaults. Stringifying structured keys could exhaust memory before the sink checks their size.
   *
   * Sensitive key text causes the value to be filtered. If the key itself was filtered because of a
   * sensitive value-class property, its original text is unavailable, so the value is filtered too.
   */
  private class MapEncoder(
    sink: ArgumentSink,
    nesting: SerializerNesting,
    private val isRoot: Boolean = false,
  ) : ValueEncoder(sink, nesting) {

    private var isKey = true
    private var isCurrentValueSensitive = false

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
      isKey = index % 2 == 0
      // Each key is encoded before its value, so isCurrentValueSensitive already applies to it.
      if (!isKey && isCurrentValueSensitive) {
        put(UrlUtils.SENSITIVE_DATA_SUBSTITUTE)
        return false
      }
      return true
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
      if (isKey) {
        throw SerializationException(
          "Back stack entry has a map key that's a structure (${descriptor.serialName}). Only " +
            "scalar map keys are supported.",
        )
      }
      return super.beginStructure(descriptor)
    }

    override fun put(value: Any?) {
      if (isKey) {
        val key = value.toString()
        // Let the sink reject oversized keys before the denylist scan allocates a lowercase copy.
        sink.key(key)
        isCurrentValueSensitive = isSensitive(key)
      } else {
        sink.scalar(value)
      }
    }

    override fun putFiltered() {
      if (isKey) {
        sink.key(UrlUtils.SENSITIVE_DATA_SUBSTITUTE)
        isCurrentValueSensitive = true
      } else {
        super.putFiltered()
      }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
      if (!isRoot) {
        sink.end()
      }
    }
  }

  /**
   * Maximum nested [Encoder.encodeSerializableValue] calls per entry.
   *
   * Allows room for property serializers and polymorphic or value-class wrappers within
   * [MAX_ARGUMENT_DEPTH]. Guards against stack overflow when serializers recurse without nesting
   * properties, which the argument depth limit can't detect.
   */
  const val MAX_SERIALIZER_NESTING: Int = 64

  /**
   * Serial names of the unsigned number types, whose serializers encode them inline as their
   * same-width signed storage value (e.g. `UInt.MAX_VALUE` as `-1`).
   */
  private val UNSIGNED_SERIAL_NAMES =
    setOf("kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong")

  /** Whether this describes a value class whose underlying property's name is sensitive. */
  private fun SerialDescriptor.hasSensitiveUnderlyingProperty(): Boolean =
    isInline && elementsCount == 1 && isSensitive(getElementName(0))

  /**
   * Discards a value class's underlying value after its filtered substitute has been recorded.
   *
   * Skips nested serializers, matching the handling of sensitive properties.
   */
  private object DiscardingEncoder : AbstractEncoder() {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeValue(value: Any) = Unit

    override fun encodeNull() = Unit

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) = Unit

    override fun <T : Any> encodeNullableSerializableValue(
      serializer: SerializationStrategy<T>,
      value: T?,
    ) = Unit
  }

  /**
   * Converts the signed storage value of an unsigned number back to its unsigned value and hands it
   * to [put]. `ULong`s beyond `Long.MAX_VALUE` become [BigInteger]s.
   */
  private class UnsignedEncoder(private val put: (Any?) -> Unit) : AbstractEncoder() {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeValue(value: Any) {
      put(
        when (value) {
          is Byte -> value.toUByte().toInt()
          is Short -> value.toUShort().toInt()
          is Int -> value.toUInt().toLong()
          is Long -> if (value >= 0) value else BigInteger(value.toULong().toString())
          else -> value
        },
      )
    }
  }

  /**
   * Limits nested [Encoder.encodeSerializableValue] calls for one entry, rejecting it through
   * [sink] when nesting exceeds [MAX_SERIALIZER_NESTING].
   *
   * After any sink failure, [enter] rethrows that failure to prevent further serializer calls, even
   * if a serializer catches the original exception.
   */
  private class SerializerNesting(private val sink: ArgumentSink) {

    private var calls = 0

    fun enter() {
      sink.failure?.let { throw it }
      if (++calls > MAX_SERIALIZER_NESTING) {
        sink.reject(ArgumentDropReason.INVALID_STRUCTURE, DropArguments.STRUCTURE_WARNING)
      }
    }

    fun exit() {
      calls--
    }
  }

  /**
   * Replaces values under sensitive keys with [UrlUtils.SENSITIVE_DATA_SUBSTITUTE] before
   * forwarding them to [sink]. Nested maps and lists under those keys are discarded.
   *
   * All encoders write through this filter to keep sensitive values out of the output. They also
   * check keys before invoking serializers, avoiding work and potential failures for values that
   * will be filtered.
   */
  internal class SensitiveKeyFilter(private val sink: ArgumentSink) : ArgumentSink {

    /** The number of open maps and lists being discarded, or 0 if none. */
    private var discardDepth = 0
    private var isNextValueSensitive = false

    override val failure: DropArguments?
      get() = sink.failure

    override fun beginMap(identity: Any?) {
      begin { sink.beginMap(identity) }
    }

    override fun beginList(identity: Any?) {
      begin { sink.beginList(identity) }
    }

    override fun key(name: String) {
      if (discardDepth > 0) {
        return
      }
      sink.key(name)
      isNextValueSensitive = isSensitive(name)
    }

    override fun scalar(value: Any?) {
      if (discardDepth > 0) {
        return
      }
      sink.scalar(if (isNextValueSensitive) UrlUtils.SENSITIVE_DATA_SUBSTITUTE else value)
      isNextValueSensitive = false
    }

    /**
     * Delegates [compute] to [sink] so limits are checked before computing the value.
     *
     * Skips computation for filtered or discarded values.
     */
    override fun computedScalar(compute: () -> Any?) {
      if (discardDepth > 0) {
        return
      }
      val isSensitive = isNextValueSensitive
      sink.computedScalar { if (isSensitive) UrlUtils.SENSITIVE_DATA_SUBSTITUTE else compute() }
      isNextValueSensitive = false
    }

    override fun end() {
      if (discardDepth > 0) {
        discardDepth--
      } else {
        sink.end()
      }
    }

    override fun reject(reason: ArgumentDropReason, warning: String): Nothing =
      sink.reject(reason, warning)

    private inline fun begin(beginInSink: () -> Unit) {
      when {
        discardDepth > 0 -> discardDepth++
        isNextValueSensitive -> {
          sink.scalar(UrlUtils.SENSITIVE_DATA_SUBSTITUTE)
          isNextValueSensitive = false
          discardDepth = 1
        }
        else -> beginInSink()
      }
    }
  }

  /** Whether [key] is sensitive and its value should be filtered. */
  private fun isSensitive(key: String): Boolean = HttpUtils.containsSensitiveDataKey(key)
}
