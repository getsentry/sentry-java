@file:OptIn(ExperimentalSerializationApi::class)

package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.ArgumentSink
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.util.ExceptionUtils
import io.sentry.util.HttpUtils
import io.sentry.util.UrlUtils
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
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
 * - Lists and sets become lists.
 *
 * - Maps whose keys are scalars, such as strings, numbers, enums, or value classes wrapping them,
 *   become maps with stringified keys.
 *
 * - Maps whose keys are structures (classes, objects, lists, maps, or polymorphic values) become
 *   lists of `{"key": <key>, "value": <value>}` entries, as a key can't be recorded as text without
 *   losing its structure. Whether a map's keys are structures is decided by the key type's
 *   descriptor, so all maps of one type have the same shape.
 *
 * - Enums use their serial names. Value classes use their underlying values.
 *
 * - `CharSequence` properties become strings, as in Nav3's saved state on Android. Their declared
 *   type is open polymorphic, but their values aren't `@Serializable`.
 *
 * - Unsigned numbers (`UByte`, `UShort`, `UInt`, `ULong`) retain their unsigned values.
 *
 * - Polymorphic properties become `{"type": <serial name>, "value": <value>}`. See "Open
 *   polymorphic values" below.
 *
 * - Entries encoded as scalars, such as by a custom string serializer, become `{"value": <value>}`.
 *
 * - Properties marked `@Transient` are omitted. Properties whose values equal their defaults are
 *   recorded, unlike in Nav3's saved state, which omits them by default.
 *
 * - A property, list element, or map value whose serializer can't encode it with this encoder
 *   becomes [UNENCODABLE_SUBSTITUTE]. See "Unencodable values" below.
 *
 * **Unencodable values**
 *
 * Some values that the app's own saved state can store can't be encoded here: contextual values,
 * and open polymorphic values whose runtime classes aren't `@Serializable` (e.g., a `Parcelable`
 * property), need serializers registered in a `SerializersModule` we don't have, and some
 * serializers only work with a specific encoder. Their serializers throw an
 * [IllegalArgumentException] (which includes [SerializationException]). Rather than dropping all of
 * the entry's arguments, the encoder records such a value as [UNENCODABLE_SUBSTITUTE] and continues
 * with the next one.
 *
 * It only does so if the serializer threw before writing anything for the value. Sinks can't undo
 * writes, so recording a value that's only partly written would show Sentry an incomplete value as
 * if it were complete. Such failures still propagate, as do failures of the entry's own serializer
 * and of scalar map keys. (A key that fails can't be replaced without risking two keys with the
 * same text. Structured keys are recorded as entry values, so they are replaced like any value.)
 *
 * **Open polymorphic values**
 *
 * kotlinx.serialization encodes a value of an open polymorphic type (e.g., a non-sealed interface)
 * with the serializer registered for its runtime class in the encoder's `SerializersModule`. We
 * don't have the app's module, so we find the runtime class's own serializer by reflection instead,
 * as Nav3's default back stack serializer does for back stack entries. This works for runtime
 * classes annotated with `@Serializable`, and writes what a registration would. Other values, e.g.,
 * a `Parcelable` that isn't `@Serializable`, still fail and become [UNENCODABLE_SUBSTITUTE].
 *
 * Unlike kotlinx.serialization, this also encodes subclasses the app didn't register, and uses a
 * subclass's own serializer even if the app registered a different one for it.
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
 * written as keys.
 *
 * Under a structured map key, there's no key text to check, so a value is filtered only by its own
 * property names. The key's own properties are filtered by their names, like any class's.
 * [SensitiveKeyFilter] checks every output key as a safeguard against missed encoder checks.
 *
 * The denylist originates from filtering HTTP headers, cookies, and query parameters. This encoder
 * applies it to navigation arguments as a baseline for sensitive-data filtering.
 */
internal object KotlinxPropertiesEncoder {

  /** Key for top-level values encoded without a map, such as by a custom serializer. */
  const val VALUE_KEY: String = "value"

  /** Recorded in place of a value whose serializer can't encode it with this encoder. */
  const val UNENCODABLE_SUBSTITUTE: String = "[Unencodable]"

  /**
   * Encodes [value] with [serializer], writing its top-level properties to [sink]'s root map.
   *
   * Throws [SerializationException] when the entry itself can't be encoded, or when a value fails
   * after its serializer wrote part of it. Values that fail before being written are recorded as
   * [UNENCODABLE_SUBSTITUTE] instead, and reported to [onUnencodableValue]. Throws [DropArguments]
   * when an argument limit is exceeded.
   *
   * Limit failures, including [MAX_SERIALIZER_NESTING], are recorded in [ArgumentSink.failure]. A
   * serializer can catch [DropArguments] and then throw a different exception or return normally.
   * Callers must check [ArgumentSink.failure] to determine whether to drop the arguments.
   */
  fun encode(
    serializer: KSerializer<Any>,
    value: Any,
    sink: ArgumentSink,
    onUnencodableValue: (IllegalArgumentException) -> Unit = {},
  ) {
    val filteredSink = SensitiveKeyFilter(sink)
    val context = EncodingContext(SerializerNesting(filteredSink), onUnencodableValue)
    RootEncoder(WriteCountingSink(filteredSink), context).encodeSerializableValue(serializer, value)
  }

  /** State shared by all the encoders that encode one entry. */
  private class EncodingContext(
    val nesting: SerializerNesting,
    val onUnencodableValue: (IllegalArgumentException) -> Unit,
  )

  /** Encodes a single value and writes it to [sink]. Structures are delegated to child encoders. */
  // Nearly all of its functions override AbstractEncoder's, whose API sets their number.
  @Suppress("TooManyFunctions")
  private open class ValueEncoder(
    protected val sink: WriteCountingSink,
    protected val context: EncodingContext,
  ) : AbstractEncoder() {

    protected val nesting: SerializerNesting
      get() = context.nesting

    override val serializersModule: SerializersModule = EmptySerializersModule()

    /**
     * Whether an element of the structure this encodes can be recorded as [UNENCODABLE_SUBSTITUTE]
     * if its serializer fails.
     */
    protected open val canSubstituteElement: Boolean
      get() = true

    override fun <T> encodeSerializableElement(
      descriptor: SerialDescriptor,
      index: Int,
      serializer: SerializationStrategy<T>,
      value: T,
    ) {
      if (encodeElement(descriptor, index)) {
        substitutingUnencodable { encodeSerializableValue(serializer, value) }
      }
    }

    override fun <T : Any> encodeNullableSerializableElement(
      descriptor: SerialDescriptor,
      index: Int,
      serializer: SerializationStrategy<T>,
      value: T?,
    ) {
      if (encodeElement(descriptor, index)) {
        substitutingUnencodable { encodeNullableSerializableValue(serializer, value) }
      }
    }

    /**
     * Runs [encodeValue], recording [UNENCODABLE_SUBSTITUTE] instead if it throws an
     * [IllegalArgumentException] before writing anything. See "Unencodable values" in
     * [KotlinxPropertiesEncoder].
     */
    private inline fun substitutingUnencodable(encodeValue: () -> Unit) {
      val writesBefore = sink.writes
      try {
        encodeValue()
      } catch (e: IllegalArgumentException) {
        // Broad within IllegalArgumentException because the value's serializer is host app or
        // library code: kotlinx.serialization throws SerializationException (a subclass) for
        // missing contextual and polymorphic serializers, and savedstate's serializers throw a
        // plain IllegalArgumentException when used with any encoder but its own. Other exceptions
        // and partially written values still drop the entry's arguments.
        //
        // A serializer may catch a limit violation and throw its own exception. The sink's
        // recorded failure takes precedence, as it decides whether older entries keep arguments.
        sink.failure?.let { throw it }
        if (!canSubstituteElement || sink.writes != writesBefore) {
          throw e
        }
        context.onUnencodableValue(e)
        put(UNENCODABLE_SUBSTITUTE)
      }
    }

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
        if (serializer.isCharSequenceSerializer() && value is CharSequence) {
          // The sink records text as a String.
          put(value)
          return
        }
        val subclassSerializer =
          if (serializer is PolymorphicSerializer<*> && value != null) {
            subclassSerializerOrNull(value.javaClass)
          } else {
            null
          }
        if (subclassSerializer != null) {
          encodePolymorphic(serializer.descriptor, subclassSerializer, value as Any)
        } else {
          serializer.serialize(this, value)
        }
      } finally {
        nesting.exit()
      }
    }

    /**
     * Encodes [value] as kotlinx.serialization's polymorphic serializers do once they've found its
     * runtime class's serializer: its serial name as the [polymorphicDescriptor]'s `type` element,
     * and its properties as the `value` element.
     */
    private fun encodePolymorphic(
      polymorphicDescriptor: SerialDescriptor,
      subclassSerializer: KSerializer<Any>,
      value: Any,
    ) {
      val composite = beginStructure(polymorphicDescriptor)
      composite.encodeStringElement(
        polymorphicDescriptor,
        0,
        subclassSerializer.descriptor.serialName,
      )
      composite.encodeSerializableElement(polymorphicDescriptor, 1, subclassSerializer, value)
      composite.endStructure(polymorphicDescriptor)
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
          ListEncoder(sink, context)
        }
        StructureKind.MAP ->
          if (descriptor.hasStructuredKeys()) {
            sink.beginList()
            MapEntryListEncoder(sink, context)
          } else {
            sink.beginMap()
            MapEncoder(sink, context)
          }
        else -> {
          sink.beginMap()
          ClassEncoder(sink, context)
        }
      }
  }

  /**
   * Encodes the entry itself. A class's properties, or a map's entries, are written to the sink's
   * root map. Anything else (e.g., a custom serializer that encodes to a string) is written under
   * [VALUE_KEY].
   */
  private class RootEncoder(sink: WriteCountingSink, context: EncodingContext) :
    ValueEncoder(sink, context) {

    override fun put(value: Any?) {
      sink.key(VALUE_KEY)
      sink.scalar(value)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
      when {
        // Lists and maps written as lists can't be the root map, so they go under VALUE_KEY.
        descriptor.kind == StructureKind.LIST ||
          (descriptor.kind == StructureKind.MAP && descriptor.hasStructuredKeys()) -> {
          sink.key(VALUE_KEY)
          super.beginStructure(descriptor)
        }
        descriptor.kind == StructureKind.MAP -> MapEncoder(sink, context, isRoot = true)
        else -> ClassEncoder(sink, context, isRoot = true)
      }
  }

  /**
   * Writes the elements of a class, object, or polymorphic value as a map.
   *
   * If [isRoot] is true, uses the sink's already-open root map.
   */
  private class ClassEncoder(
    sink: WriteCountingSink,
    context: EncodingContext,
    private val isRoot: Boolean = false,
  ) : ValueEncoder(sink, context) {

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
  private class ListEncoder(sink: WriteCountingSink, context: EncodingContext) :
    ValueEncoder(sink, context) {

    override fun endStructure(descriptor: SerialDescriptor) {
      sink.end()
    }
  }

  /**
   * Writes alternating map keys and values. If [isRoot] is true, uses the sink's already-open root
   * map.
   *
   * Keys must be scalars and are recorded as strings. Maps whose key type is a structure are
   * written by [MapEntryListEncoder] instead. A key that begins a structure anyway (e.g., a custom
   * serializer whose descriptor claims a scalar) is rejected rather than stringified, which could
   * exhaust memory before the sink checks its size.
   *
   * Sensitive key text causes the value to be filtered. If the key itself was filtered because of a
   * sensitive value-class property, its original text is unavailable, so the value is filtered too.
   */
  private class MapEncoder(
    sink: WriteCountingSink,
    context: EncodingContext,
    private val isRoot: Boolean = false,
  ) : ValueEncoder(sink, context) {

    private var isKey = true
    private var isCurrentValueSensitive = false

    /** Only values: a map can't be recorded without its keys. */
    override val canSubstituteElement: Boolean
      get() = !isKey

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
   * Writes a map whose keys are structures as a list of `{"key": <key>, "value": <value>}` entries.
   *
   * kotlinx.serialization encodes a map's keys and values as alternating elements, so each key
   * element opens an entry map and each value element is written into it. An entry is closed when
   * the next one opens or the map ends.
   *
   * Keys and values are written like any other value, so the sink's limits and the encoder's
   * sensitive-data checks apply to their contents, and either can be replaced with
   * [UNENCODABLE_SUBSTITUTE] if its serializer fails.
   *
   * A key has no text to check against the denylist, so its value is filtered only by its own
   * property names. The exception matches [MapEncoder]: if the whole key is filtered (a value class
   * whose underlying property's name is sensitive), its value is filtered too.
   */
  private class MapEntryListEncoder(sink: WriteCountingSink, context: EncodingContext) :
    ValueEncoder(sink, context) {

    private var isEntryOpen = false
    private var isKey = true
    private var isCurrentValueSensitive = false

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
      isKey = index % 2 == 0
      if (isKey) {
        endEntry()
        sink.beginMap()
        isEntryOpen = true
        isCurrentValueSensitive = false
        sink.encoderKey(ENTRY_KEY)
        return true
      }
      sink.encoderKey(ENTRY_VALUE)
      if (isCurrentValueSensitive) {
        put(UrlUtils.SENSITIVE_DATA_SUBSTITUTE)
        return false
      }
      return true
    }

    override fun putFiltered() {
      if (isKey) {
        isCurrentValueSensitive = true
      }
      super.putFiltered()
    }

    override fun endStructure(descriptor: SerialDescriptor) {
      endEntry()
      sink.end()
    }

    private fun endEntry() {
      if (isEntryOpen) {
        sink.end()
        isEntryOpen = false
      }
    }

    private companion object {
      const val ENTRY_KEY = "key"
      const val ENTRY_VALUE = "value"
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

  /**
   * Whether this map descriptor's keys are structures, which [MapEncoder] can't record as text.
   *
   * Value classes are judged by their underlying type, as they're encoded as their underlying
   * value. Contextual keys count as scalars: their serializers are unknown here, and fail anyway
   * without a registered serializer.
   */
  private fun SerialDescriptor.hasStructuredKeys(): Boolean = getElementDescriptor(0).isStructured()

  private fun SerialDescriptor.isStructured(): Boolean =
    when {
      isInline && elementsCount == 1 -> getElementDescriptor(0).isStructured()
      // CharSequence keys are encoded as text, like strings.
      isCharSequenceDescriptor() -> false
      else -> kind is StructureKind || kind is PolymorphicKind
    }

  /**
   * The descriptor of kotlinx.serialization's serializer for `CharSequence` properties, which only
   * works with a `SerializersModule` that registers the values' classes. Nav3 apps don't need one,
   * as saved state encodes `CharSequence` values itself on Android.
   */
  private val CHAR_SEQUENCE_DESCRIPTOR: SerialDescriptor =
    PolymorphicSerializer(CharSequence::class).descriptor

  /**
   * Whether this is [CHAR_SEQUENCE_DESCRIPTOR], or its nullable form, whose serial name has a `?`
   * suffix and which isn't equal to it.
   */
  private fun SerialDescriptor.isCharSequenceDescriptor(): Boolean =
    kind == PolymorphicKind.OPEN &&
      serialName.removeSuffix("?") == CHAR_SEQUENCE_DESCRIPTOR.serialName

  private fun SerializationStrategy<*>.isCharSequenceSerializer(): Boolean =
    this is PolymorphicSerializer<*> && baseClass == CharSequence::class

  /**
   * The serializers that [subclassSerializerOrNull] found for runtime classes, or [NO_SERIALIZER].
   * Bounded by the number of classes the app's open polymorphic values have.
   */
  private val subclassSerializers = ConcurrentHashMap<Class<*>, Any>()

  private val NO_SERIALIZER = Any()

  /**
   * Returns the serializer of [type], an open polymorphic value's runtime class, if it's annotated
   * with `@Serializable` and its serializer can be found by reflection. See "Open polymorphic
   * values" in [KotlinxPropertiesEncoder].
   */
  @Suppress("UNCHECKED_CAST")
  private fun subclassSerializerOrNull(type: Class<*>): KSerializer<Any>? =
    subclassSerializers.getOrPut(type) { lookUpSubclassSerializer(type) ?: NO_SERIALIZER }
      as? KSerializer<Any>

  private fun lookUpSubclassSerializer(type: Class<*>): KSerializer<*>? =
    try {
      if (type.isAnnotationPresent(Serializable::class.java)) {
        reflectiveSerializerOrNull(type)
      } else {
        null
      }
    } catch (_: LinkageError) {
      // Caught before rethrowIfFatal, which rethrows LinkageErrors. A class that can't be linked
      // fails like one without a serializer, and so does the next case.
      null
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
      // Broad because looking up a serializer can run the app's serializer initialization, which
      // may throw anything. Without a serializer, the value is encoded with its declared
      // polymorphic serializer, which fails and records the value as UNENCODABLE_SUBSTITUTE.
      ExceptionUtils.rethrowIfFatal(t)
      null
    }

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
   * Forwards to [sink], counting the values, keys, and structure boundaries written, so that an
   * encoder can tell whether a failing serializer wrote part of its value.
   */
  private class WriteCountingSink(private val sink: SensitiveKeyFilter) : ArgumentSink {

    var writes: Long = 0
      private set

    override val failure: DropArguments?
      get() = sink.failure

    override fun beginMap(identity: Any?) {
      writes++
      sink.beginMap(identity)
    }

    override fun beginList(identity: Any?) {
      writes++
      sink.beginList(identity)
    }

    override fun key(name: String) {
      writes++
      sink.key(name)
    }

    /**
     * Writes a key the encoder chose itself, unfiltered, as for [SensitiveKeyFilter.encoderKey].
     */
    fun encoderKey(name: String) {
      writes++
      sink.encoderKey(name)
    }

    override fun scalar(value: Any?) {
      writes++
      sink.scalar(value)
    }

    override fun computedScalar(compute: () -> Any?) {
      writes++
      sink.computedScalar(compute)
    }

    override fun end() {
      writes++
      sink.end()
    }

    override fun reject(reason: ArgumentDropReason, warning: String): Nothing =
      sink.reject(reason, warning)
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

    /**
     * Writes [name] as a key without checking it against the denylist.
     *
     * For keys the encoder chooses itself rather than takes from the entry, such as the `"key"`
     * field of a structured map entry. Those aren't app data, and some are on the denylist (`"key"`
     * matches it), so checking them would filter every value written under them.
     */
    fun encoderKey(name: String) {
      if (discardDepth > 0) {
        return
      }
      sink.key(name)
      isNextValueSensitive = false
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
