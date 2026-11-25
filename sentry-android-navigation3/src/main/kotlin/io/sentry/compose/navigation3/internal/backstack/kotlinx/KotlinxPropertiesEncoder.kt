@file:OptIn(ExperimentalSerializationApi::class)

package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.ArgumentSink
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.util.HttpUtils
import io.sentry.util.UrlUtils
import java.math.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
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
 *   [UNENCODABLE_SUBSTITUTE], as such a key can't be recorded as text. Whether a map's keys are
 *   structures is decided by the key type's descriptor, before the map is encoded.
 *
 * - Enums use their serial names. Value classes use their underlying values.
 *
 * - `CharSequence` properties become strings, as in Nav3's saved state on Android. Their declared
 *   type is open polymorphic, but their values aren't `@Serializable`.
 *
 * - Unsigned numbers (`UByte`, `UShort`, `UInt`, `ULong`) retain their unsigned values.
 *
 * - Sealed properties become `{"type": <serial name>, "value": <value>}`. Open polymorphic values,
 *   other than `CharSequence`s, become [UNENCODABLE_SUBSTITUTE]; see "Unencodable values" below.
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
 * Some values that the app's own saved state can store can't be encoded here: contextual values and
 * open polymorphic values (e.g., a non-sealed interface or a `Parcelable` property) need
 * serializers registered in a `SerializersModule` we don't have, and some serializers only work
 * with a specific encoder. Their serializers throw an [IllegalArgumentException] (which includes
 * [SerializationException]), as may a `CharSequence` value's `toString()`. Rather than dropping all
 * of the entry's arguments, the encoder records such a value as [UNENCODABLE_SUBSTITUTE] and
 * continues with the next one.
 *
 * Substitution requires that the serializer failed before writing any part of the value, because
 * sinks can't undo writes. Partial writes, failures of the entry's own serializer, and failures of
 * scalar map keys propagate instead, as replacing a key could create duplicate keys.
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
 * [SensitiveKeyFilter] checks every key written to the output, and the encoders ask it whether a
 * value will be filtered before encoding it.
 *
 * [ValueEncoder.encodeInline] also checks value classes' underlying property names, which aren't
 * written as keys. A map whose key type is such a value class is recorded as
 * [UrlUtils.SENSITIVE_DATA_SUBSTITUTE] as a whole, as its keys would have no text left.
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
   * Propagates serializer failures for the entry itself, scalar map keys, or partially written
   * values. Eligible values that fail before being written are recorded as [UNENCODABLE_SUBSTITUTE]
   * instead, and reported to [onUnencodableValue]. Throws [DropArguments] when an argument limit is
   * exceeded.
   *
   * Limit failures, including [MAX_SERIALIZER_NESTING], are recorded in [ArgumentSink.failure]. A
   * serializer can catch [DropArguments] and then throw a different exception or return normally.
   * Callers must check [ArgumentSink.failure] to determine whether to drop the arguments.
   */
  fun encode(
    serializer: KSerializer<Any>,
    value: Any,
    sink: ArgumentSink,
    onUnencodableValue: () -> Unit = {},
  ) {
    val filteredSink = SensitiveKeyFilter(sink)
    val context = EncodingContext(SerializerNesting(filteredSink), onUnencodableValue)
    RootEncoder(WriteCountingSink(filteredSink), context).encodeSerializableValue(serializer, value)
  }

  /** State shared by all the encoders that encode one entry. */
  private class EncodingContext(
    val nesting: SerializerNesting,
    val onUnencodableValue: () -> Unit,
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
     * Whether an element of the structure this encodes can be recorded as a substitute: as
     * [UNENCODABLE_SUBSTITUTE] if its serializer fails, or as a map's substitute (see [MapKeys]).
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
        sink.throwIfFailed()
        if (!canSubstituteElement || sink.writes != writesBefore) {
          throw e
        }
        context.onUnencodableValue()
        putUnencodable()
      }
    }

    /** Records [UNENCODABLE_SUBSTITUTE] in place of an element whose serializer failed. */
    private fun putUnencodable() {
      put(UNENCODABLE_SUBSTITUTE)
    }

    protected open fun put(value: Any?) {
      sink.scalar(value)
    }

    /** Replaces a filtered value with [UrlUtils.SENSITIVE_DATA_SUBSTITUTE]. */
    protected open fun putFiltered() {
      put(UrlUtils.SENSITIVE_DATA_SUBSTITUTE)
    }

    /**
     * Returns whether the value under the key just written should be encoded, for [encodeElement].
     * If the sink will filter that value, records the substitute and returns `false` instead, so
     * that the value's own serializers don't run.
     */
    protected fun encodesValue(): Boolean {
      if (sink.isNextValueSensitive) {
        put(UrlUtils.SENSITIVE_DATA_SUBSTITUTE)
        return false
      }
      return true
    }

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
      nesting.enter()
      try {
        when {
          // The sink records text as a String.
          serializer.isCharSequenceSerializer() && value is CharSequence -> put(value)
          // Checked before the map's serializer runs, as it iterates over every entry even when
          // beginStructure returns DiscardingEncoder. A map key can't be a substitute.
          canSubstituteElement && putMapSubstitute(serializer.descriptor) -> Unit
          else -> serializer.serialize(this, value)
        }
      } finally {
        nesting.exit()
      }
    }

    /**
     * If [descriptor] describes a map whose keys can't be recorded as text (see [MapKeys]), records
     * the map's substitute and returns `true`. Returns `false` for anything else.
     */
    private fun putMapSubstitute(descriptor: SerialDescriptor): Boolean {
      if (descriptor.kind != StructureKind.MAP) {
        return false
      }
      when (descriptor.mapKeys()) {
        MapKeys.TEXT -> return false
        MapKeys.SENSITIVE -> put(UrlUtils.SENSITIVE_DATA_SUBSTITUTE)
        MapKeys.STRUCTURED -> {
          context.onUnencodableValue()
          put(UNENCODABLE_SUBSTITUTE)
        }
      }
      return true
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
        // encodeSerializableValue already records substitutes for maps whose serializers describe
        // them. This catches the rest (e.g., a custom serializer that encodes a map without
        // describing itself as one), whose entries the serializer then iterates into
        // DiscardingEncoder. Substitutes are recorded before the map writes anything, so they
        // replace the whole map rather than dropping the entry's arguments.
        StructureKind.MAP ->
          if (putMapSubstitute(descriptor)) {
            DiscardingEncoder
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
        // Lists can't be the root map, so they go under VALUE_KEY.
        descriptor.kind == StructureKind.LIST -> {
          sink.key(VALUE_KEY)
          super.beginStructure(descriptor)
        }
        // Recorded as a substitute under VALUE_KEY, through put().
        descriptor.kind == StructureKind.MAP && descriptor.mapKeys() != MapKeys.TEXT ->
          super.beginStructure(descriptor)
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
      sink.key(descriptor.getElementName(index))
      return encodesValue()
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
   * Keys must be scalars and are recorded as strings. Maps whose key type is a structure, or a
   * value class with a sensitive underlying property, are recorded as a substitute instead (see
   * [MapKeys]). A key that begins a structure anyway (e.g., a custom serializer whose descriptor
   * claims a scalar) is rejected rather than stringified, which could exhaust memory before the
   * sink checks its size.
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

    /** Only values: a map can't be recorded without its keys. */
    override val canSubstituteElement: Boolean
      get() = !isKey

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
      isKey = index % 2 == 0
      // Each key is written to the sink before its value is encoded, so the sink already knows
      // whether the value is sensitive.
      return isKey || encodesValue()
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
        if (value is CharSequence && value.length > MAX_ARGUMENT_CHARACTERS) {
          // The sink checks a key's length only once it's text, so we reject a CharSequence key
          // that can't fit in any budget before copying it.
          sink.reject(ArgumentDropReason.CHARACTER_LIMIT, DropArguments.MAX_CHARACTER_WARNING)
        }
        // The sink rejects oversized keys before the denylist scan allocates a lowercase copy.
        sink.key(value.toString())
      } else {
        sink.scalar(value)
      }
    }

    override fun putFiltered() {
      if (isKey) {
        // Maps whose key type is filtered are replaced before they're encoded, so this is only
        // reached for a custom key serializer whose descriptor doesn't describe what it encodes.
        // A second such key in the map is a duplicate key.
        sink.key(UrlUtils.SENSITIVE_DATA_SUBSTITUTE)
        // The key's text is gone, so the sink can't tell whether its value is sensitive.
        sink.markNextValueSensitive()
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

  /** How a map's keys are recorded, decided by the key type's descriptor. See [mapKeys]. */
  private enum class MapKeys {
    /** As text, by [MapEncoder]. */
    TEXT,

    /**
     * Not at all: the key type is a value class whose underlying property's name (or that of a
     * value class it wraps) is sensitive, so each key would be recorded as
     * [UrlUtils.SENSITIVE_DATA_SUBSTITUTE] and two keys would collide. The whole map is recorded as
     * [UrlUtils.SENSITIVE_DATA_SUBSTITUTE] instead, which reveals no more about it.
     */
    SENSITIVE,

    /**
     * Not at all: the key type is a structure (a class, object, list, map, or polymorphic value),
     * which can't be recorded as text. The whole map is recorded as [UNENCODABLE_SUBSTITUTE].
     */
    STRUCTURED,
  }

  /**
   * Returns how this map descriptor's keys are recorded.
   *
   * Value classes are judged by their underlying type, as they're encoded as their underlying
   * value, and are checked for sensitive underlying property names as [ValueEncoder.encodeInline]
   * checks them. Contextual keys count as scalars: their serializers are unknown here, and fail
   * anyway without a registered serializer.
   *
   * A custom descriptor can claim to wrap itself, or descriptors can wrap each other in a cycle, so
   * a key type that wraps more than [MAX_SERIALIZER_NESTING] value classes counts as a structure
   * instead of being unwrapped further. Encoding a key nested that deeply would exceed the same
   * limit on nested serializer calls anyway.
   */
  private fun SerialDescriptor.mapKeys(): MapKeys {
    var keyDescriptor = getElementDescriptor(0)
    var unwrapped = 0
    while (keyDescriptor.isInline && keyDescriptor.elementsCount == 1) {
      if (keyDescriptor.hasSensitiveUnderlyingProperty()) {
        return MapKeys.SENSITIVE
      }
      if (++unwrapped > MAX_SERIALIZER_NESTING) {
        return MapKeys.STRUCTURED
      }
      keyDescriptor = keyDescriptor.getElementDescriptor(0)
    }
    return when {
      // CharSequence keys are encoded as text, like strings.
      keyDescriptor.isCharSequenceDescriptor() -> MapKeys.TEXT
      keyDescriptor.kind is StructureKind || keyDescriptor.kind is PolymorphicKind ->
        MapKeys.STRUCTURED
      else -> MapKeys.TEXT
    }
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
   *
   * Scalars are counted only once [sink] accepts them. A sink adds nothing for a scalar whose
   * conversion fails (e.g., a `CharSequence` whose `toString()` throws), so the encoder can still
   * record [UNENCODABLE_SUBSTITUTE] in its place. The failed scalar still counts towards the
   * entry's value limit.
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

    override fun key(name: String, describeDuplicate: (() -> String)?) {
      writes++
      sink.key(name, describeDuplicate)
    }

    /** As for [SensitiveKeyFilter.isNextValueSensitive]. */
    val isNextValueSensitive: Boolean
      get() = sink.isNextValueSensitive

    /** As for [SensitiveKeyFilter.markNextValueSensitive]. Not a write, so it isn't counted. */
    fun markNextValueSensitive() {
      sink.markNextValueSensitive()
    }

    override fun scalar(value: Any?) {
      sink.scalar(value)
      writes++
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
      sink.throwIfFailed()
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
   * This is the only place that checks keys against the denylist. All encoders write through it, so
   * no sensitive value reaches the output even if an encoder writes one. Encoders also read
   * [isNextValueSensitive] after writing a key, so that they can record the substitute without
   * running the value's serializers, which saves work and avoids their failures.
   */
  internal class SensitiveKeyFilter(private val sink: ArgumentSink) : ArgumentSink {

    /** The number of open maps and lists being discarded, or 0 if none. */
    private var discardDepth = 0

    /**
     * Whether the next value will be replaced with [UrlUtils.SENSITIVE_DATA_SUBSTITUTE], because
     * the key just written is on the denylist or [markNextValueSensitive] was called. Cleared once
     * that value is written.
     */
    var isNextValueSensitive: Boolean = false
      private set

    /**
     * Makes the next value be filtered even though its key isn't on the denylist. For a value whose
     * key had no text to check because the key itself was filtered.
     */
    fun markNextValueSensitive() {
      if (discardDepth == 0) {
        isNextValueSensitive = true
      }
    }

    override val failure: DropArguments?
      get() = sink.failure

    override fun beginMap(identity: Any?) {
      begin { sink.beginMap(identity) }
    }

    override fun beginList(identity: Any?) {
      begin { sink.beginList(identity) }
    }

    override fun key(name: String, describeDuplicate: (() -> String)?) {
      if (discardDepth > 0) {
        return
      }
      sink.key(name, describeDuplicate)
      isNextValueSensitive = isSensitive(name)
    }

    override fun scalar(value: Any?) {
      if (discardDepth > 0) {
        return
      }
      sink.scalar(if (isNextValueSensitive) UrlUtils.SENSITIVE_DATA_SUBSTITUTE else value)
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
