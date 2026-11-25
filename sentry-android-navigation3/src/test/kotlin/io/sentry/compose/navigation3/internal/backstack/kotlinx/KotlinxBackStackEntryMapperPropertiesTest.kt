package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryLevel
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.ArgumentSource
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING
import kotlin.test.assertFailsWith
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import org.junit.Test
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@Suppress("LargeClass")
class KotlinxBackStackEntryMapperPropertiesTest {

  @Serializable @SerialName("PropertyLess") class PropertyLess

  /** Encodes to its code alone, a string rather than a structure. */
  @Serializable(with = CodeSerializer::class) class Code(val code: String)

  object CodeSerializer : KSerializer<Code> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("Code", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Code) {
      encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): Code = Code(decoder.decodeString())
  }

  /**
   * Serializes [PropertyLess] with its own descriptor, except that reading its `kind` throws
   * [kindError].
   */
  @OptIn(ExperimentalSerializationApi::class)
  class KindThrowingPropertyLessSerializer(kindError: Throwable) : KSerializer<PropertyLess> {
    private val delegate = PropertyLess.serializer().descriptor

    override val descriptor: SerialDescriptor =
      Mockito.mock(SerialDescriptor::class.java, delegatesTo<Any>(delegate)).apply {
        doThrow(kindError).whenever(this).kind
      }

    override fun serialize(encoder: Encoder, value: PropertyLess) {
      encoder.encodeStructure(descriptor) {}
    }

    override fun deserialize(decoder: Decoder): PropertyLess = throw UnsupportedOperationException()
  }

  interface OpenOrigin

  /** Can't be encoded as an [OpenOrigin] without a module. */
  class SearchOrigin : OpenOrigin

  @Serializable
  @SerialName("WithOpenPolymorphic")
  data class WithOpenPolymorphic(val id: String, @Polymorphic val origin: OpenOrigin)

  /** Encodes to `"partial"`, and then, if it [fails], throws, so it's written partway. */
  @Serializable(with = FragileSerializer::class) class Fragile(val fails: Boolean)

  object FragileSerializer : KSerializer<Fragile> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("Fragile", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Fragile) {
      encoder.encodeString("partial")
      if (value.fails) throw SerializationException("failed partway")
    }

    override fun deserialize(decoder: Decoder): Fragile = throw UnsupportedOperationException()
  }

  /** Has a contextual property, and a property that fails the whole entry when [Fragile.fails]. */
  @Serializable
  @SerialName("WithContextualAndFragile")
  internal data class WithContextualAndFragile(
    @Contextual val other: Unregistered?,
    val fragile: Fragile,
  )

  @Serializable
  @SerialName("TabWithBodies")
  enum class TabWithBodies {
    FEED {
      override val icon = "feed"
    },
    @SerialName("tab.search")
    SEARCH {
      override val icon = "search"
    };

    abstract val icon: String
  }

  /** Can't be encoded: its serializer throws an exception that names its [id]. */
  @Serializable(with = RejectingSerializer::class) class Rejecting(val id: String)

  object RejectingSerializer : KSerializer<Rejecting> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("Rejecting", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Rejecting) {
      throw IllegalArgumentException("Invalid ${value.id}", IllegalStateException(value.id))
    }

    override fun deserialize(decoder: Decoder): Rejecting = throw UnsupportedOperationException()
  }

  @Serializable
  @SerialName("WithRejecting")
  data class WithRejecting(val id: String, val rejecting: Rejecting)

  @Serializable data class Point(val x: Int)

  @Serializable
  @SerialName("WithPointMapKeys")
  data class WithPointMapKeys(val id: String, val byPoint: Map<Point, Int>, val page: Int)

  @Serializable @SerialName("WithValues") data class WithValues(val values: List<Int>)

  @Serializable
  @SerialName("WithNullableMapKeys")
  data class WithNullableMapKeys(val byName: Map<String?, Int>)

  /**
   * Holds [boxes], nested [Box]es. Its type isn't generic, unlike [Box], so the mapper looks up its
   * serializer, which tests replace with a [BoxesSerializer].
   */
  @Serializable class WithBoxes(@Contextual val boxes: Any)

  /** Serializes a [WithBoxes] as its [WithBoxes.boxes], with [boxesSerializer]. */
  class BoxesSerializer(private val boxesSerializer: KSerializer<Any>) : KSerializer<WithBoxes> {
    override val descriptor: SerialDescriptor = boxesSerializer.descriptor

    override fun serialize(encoder: Encoder, value: WithBoxes) {
      encoder.encodeSerializableValue(boxesSerializer, value.boxes)
    }

    override fun deserialize(decoder: Decoder): WithBoxes = throw UnsupportedOperationException()
  }

  /** Serializers of this suite's own types, which tests add to the fixture's lookup. */
  private val otherSerializers: Map<Class<*>, KSerializer<*>> =
    mapOf(
      WithOpenPolymorphic::class.java to WithOpenPolymorphic.serializer(),
      WithContextualAndFragile::class.java to WithContextualAndFragile.serializer(),
      WithPointMapKeys::class.java to WithPointMapKeys.serializer(),
      WithRejecting::class.java to WithRejecting.serializer(),
      WithValues::class.java to WithValues.serializer(),
      WithNullableMapKeys::class.java to WithNullableMapKeys.serializer(),
    )

  /** An id over the argument character limit. */
  private val overLimitId = "x".repeat(MAX_ARGUMENT_CHARACTERS + 1)

  // Properties

  @Test
  fun `records encoded properties as arguments when properties are included`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper

    val mapped = listOf(mapper.map(Detail("1")), mapper.map(Detail("2")))

    assertThat(mapped)
      .containsExactly(
        SentryBackStackEntry("Detail", mapOf("id" to "1")),
        SentryBackStackEntry("Detail", mapOf("id" to "2")),
      )
      .inOrder()
  }

  @Test
  fun `doesn't encode properties when properties are excluded`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = false)
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail"))
    assertThat(fixture.detailSerializer.serialized).isEmpty()
  }

  @Test
  fun `records no arguments for objects`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper

    val mapped = mapper.map(Home)

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Home", arguments = null))
  }

  @Test
  fun `records no arguments for classes without properties`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { PropertyLess.serializer() }
    val mapper = fixture.mapper

    val mapped = mapper.map(PropertyLess())

    assertThat(mapped).isEqualTo(SentryBackStackEntry("PropertyLess", arguments = null))
  }

  @Test
  fun `records the value of entries whose serializer doesn't encode a class`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { CodeSerializer }
    val mapper = fixture.mapper

    val mapped = mapper.map(Code("abc"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Code", mapOf("value" to "abc")))
  }

  @Test
  fun `records the constant of an enum constant that has a body using its enum's serializer`() {
    // The constant's class is an anonymous subclass of the enum, which isn't @Serializable itself.
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { if (it == TabWithBodies::class.java) TabWithBodies.serializer() else null }
    val mapper = fixture.mapper

    val mapped = mapper.map(TabWithBodies.SEARCH)

    assertThat(mapped)
      .isEqualTo(SentryBackStackEntry("TabWithBodies", mapOf("value" to "tab.search")))
    assertThat(fixture.lookedUpTypes).containsExactly(TabWithBodies::class.java)
  }

  @Test
  fun `doesn't encode properties of entries that can't be named`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { null }
    val mapper = fixture.mapper

    val mapped = mapper.mapAndReadArguments(Detail("1"))

    assertThat(mapped).isNull()
    assertThat(fixture.detailSerializer.serialized).isEmpty()
  }

  @Test
  fun `keeps recording arguments for other types after a type's name lookup throws a LinkageError`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = {
      if (it == Detail::class.java) throw NoSuchMethodError() else fixture.serializers[it]
    }
    val mapper = fixture.mapper
    mapper.map(Detail("1"))

    val mapped = mapper.map(Search("shoes"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Search", mapOf("query" to "shoes")))
  }

  // Property values that can't be encoded

  @Test
  fun `records a map with class keys as the Unencodable substitute beside the other arguments`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { otherSerializers[it] ?: fixture.serializers[it] }
    val mapper = fixture.mapper

    val mapped = mapper.map(WithPointMapKeys("1", mapOf(Point(1) to 2), page = 3))

    assertThat(mapped)
      .isEqualTo(
        SentryBackStackEntry(
          "WithPointMapKeys",
          mapOf("id" to "1", "byPoint" to "[Unencodable]", "page" to 3),
        ),
      )
    verifyWarningLoggedOnce(fixture, UNENCODABLE_VALUES, WithPointMapKeys::class.java)
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `logs only an unencodable-values warning, once per type, for values recorded as the Unencodable substitute`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { otherSerializers[it] ?: fixture.serializers[it] }
    val mapper = fixture.mapper

    repeat(3) {
      mapper.mapAndReadArguments(WithContextual("$it", Unregistered("$it")))
      mapper.mapAndReadArguments(WithOpenPolymorphic("$it", SearchOrigin()))
    }

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(UNENCODABLE_VALUES) },
        eq(WithContextual::class.java.name),
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(UNENCODABLE_VALUES) },
        eq(WithOpenPolymorphic::class.java.name),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `doesn't log that values were recorded as the Unencodable substitute until the arguments are read`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper

    mapper.map(WithContextual("1", Unregistered("2")))

    verifyNoInteractions(fixture.logger)
  }

  @Test
  fun `logs the unencodable-values warning separately from the unsupported-properties warning for the same type`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { otherSerializers[it] ?: fixture.serializers[it] }
    val mapper = fixture.mapper
    mapper.mapAndReadArguments(WithContextualAndFragile(Unregistered("1"), Fragile(fails = false)))

    mapper.mapAndReadArguments(WithContextualAndFragile(null, Fragile(fails = true)))

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(UNENCODABLE_VALUES) },
        eq(WithContextualAndFragile::class.java.name),
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(UNSUPPORTED_PROPERTIES) },
        eq(WithContextualAndFragile::class.java.name),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  // Properties that can't be encoded

  @Test
  fun `keeps encoding a type's properties after their encoding throws once`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = SerializationException("unencodable")
    val mapper = fixture.mapper
    mapper.mapAndReadArguments(Detail("1"))
    fixture.detailSerializer.error = null

    val mapped = mapper.map(Detail("2"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail", mapOf("id" to "2")))
  }

  @Test
  fun `logs an unsupported-properties warning once per type whose properties can't be encoded`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = SerializationException("detail unencodable")
    fixture.searchSerializer.error = SerializationException("search unencodable")
    val mapper = fixture.mapper

    repeat(3) {
      mapper.mapAndReadArguments(Detail("$it"))
      mapper.mapAndReadArguments(Search("$it"))
    }

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(UNSUPPORTED_PROPERTIES) },
        eq(Detail::class.java.name),
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(UNSUPPORTED_PROPERTIES) },
        eq(Search::class.java.name),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `doesn't log that a type's properties can't be encoded until its arguments are read`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = SerializationException("unencodable")
    val mapper = fixture.mapper

    mapper.map(Detail("1"))

    verifyNoInteractions(fixture.logger)
  }

  // Properties over the argument limits

  @Test
  fun `keeps the name and records only the broken limit when an entry's properties exceed the argument limits`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail(overLimitId))

    assertThat(mapped)
      .isEqualTo(SentryBackStackEntry("Detail", droppedBy(ArgumentDropReason.CHARACTER_LIMIT)))
  }

  @Test
  fun `keeps encoding a type's properties after one entry's properties exceeded the argument limits`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper
    mapper.mapAndReadArguments(Detail(overLimitId))

    val mapped = mapper.map(Detail("2"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail", mapOf("id" to "2")))
  }

  @Test
  fun `logs a warning with the drop reason once per type whose properties exceed the argument limits`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper

    repeat(3) {
      mapper.mapAndReadArguments(Detail(overLimitId))
      mapper.mapAndReadArguments(Search(overLimitId))
    }

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        any<String>(),
        eq(Detail::class.java.name),
        eq(ArgumentDropReason.CHARACTER_LIMIT.serializedValue),
        any<String>(),
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        any<String>(),
        eq(Search::class.java.name),
        eq(ArgumentDropReason.CHARACTER_LIMIT.serializedValue),
        any<String>(),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `logs a warning for each different argument limit that entries of the same type break`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { otherSerializers[it] ?: fixture.serializers[it] }
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(WithNullableMapKeys(mapOf(overLimitId to 1)))
    mapper.mapAndReadArguments(WithNullableMapKeys(mapOf(null to 1, "null" to 2)))

    assertThat(
        overLimitsWarningLimit(
          fixture,
          WithNullableMapKeys::class.java,
          ArgumentDropReason.CHARACTER_LIMIT,
        ),
      )
      .isEqualTo("its keys and text exceed $MAX_ARGUMENT_CHARACTERS characters")
    assertThat(
        overLimitsWarningLimit(
          fixture,
          WithNullableMapKeys::class.java,
          ArgumentDropReason.DUPLICATE_KEY,
        ),
      )
      .isEqualTo("a map has two keys that are recorded as the same text")
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `describes the value limit in the warning for an entry with too many values`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { otherSerializers[it] ?: fixture.serializers[it] }
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(WithValues(List(MAX_ARGUMENT_VALUES + 1) { it }))

    assertThat(
        overLimitsWarningLimit(fixture, WithValues::class.java, ArgumentDropReason.MAX_COUNT),
      )
      .isEqualTo("it has more than $MAX_ARGUMENT_VALUES values")
  }

  @Test
  fun `describes the character limit in the warning for an entry with too many characters`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(Detail(overLimitId))

    assertThat(
        overLimitsWarningLimit(fixture, Detail::class.java, ArgumentDropReason.CHARACTER_LIMIT),
      )
      .isEqualTo("its keys and text exceed $MAX_ARGUMENT_CHARACTERS characters")
  }

  @Test
  fun `describes the duplicate keys in the warning for an entry with map keys recorded as the same text`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { otherSerializers[it] ?: fixture.serializers[it] }
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(WithNullableMapKeys(mapOf(null to 1, "null" to 2)))

    assertThat(
        overLimitsWarningLimit(
          fixture,
          WithNullableMapKeys::class.java,
          ArgumentDropReason.DUPLICATE_KEY,
        ),
      )
      .isEqualTo("a map has two keys that are recorded as the same text")
  }

  @Test
  fun `describes the nesting limits in the warning for an entry that nests values too deep`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val (boxesSerializer, boxes) =
      boxed(depth = MAX_ARGUMENT_DEPTH + 1, value = "leaf", String.serializer())
    fixture.lookUp = { if (it == WithBoxes::class.java) BoxesSerializer(boxesSerializer) else null }
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(WithBoxes(boxes))

    assertThat(
        overLimitsWarningLimit(
          fixture,
          WithBoxes::class.java,
          ArgumentDropReason.INVALID_STRUCTURE,
        ),
      )
      .isEqualTo(
        "it nests values more than $MAX_ARGUMENT_DEPTH levels deep, or serializers or value " +
          "classes more than $MAX_SERIALIZER_NESTING deep",
      )
  }

  @Test
  fun `logs the over-limits warning separately from the unencodable warning for the same type`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper
    mapper.mapAndReadArguments(Detail(overLimitId))
    fixture.detailSerializer.error = SerializationException("unencodable")

    mapper.mapAndReadArguments(Detail("2"))

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        any<String>(),
        eq(Detail::class.java.name),
        eq(ArgumentDropReason.CHARACTER_LIMIT.serializedValue),
        any<String>(),
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(UNSUPPORTED_PROPERTIES) },
        eq(Detail::class.java.name),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `doesn't log that an entry's properties exceed the argument limits until its arguments are read`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper

    mapper.map(Detail(overLimitId))

    verifyNoInteractions(fixture.logger)
  }

  // Property encoding that throws a LinkageError

  @Test
  fun `keeps encoding a type's properties after one entry's encoding throws a LinkageError`() {
    // A serializer may only reach a class that fails to link for some values (e.g., one subtype of
    // a sealed property), so a LinkageError only drops the arguments of the entry whose encoding
    // threw it, and leaves other types alone.
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = NoClassDefFoundError()
    fixture.detailSerializer.failsFor = { it.id == "unlinkable" }
    val mapper = fixture.mapper

    val mapped =
      listOf(
        mapper.mapAndReadArguments(Detail("unlinkable")),
        mapper.mapAndReadArguments(Search("shoes")),
        mapper.mapAndReadArguments(Detail("2")),
      )

    assertThat(mapped)
      .containsExactly(
        SentryBackStackEntry("Detail", droppedBy(ArgumentDropReason.ENCODING_FAILED)),
        SentryBackStackEntry("Search", mapOf("query" to "shoes")),
        SentryBackStackEntry("Detail", mapOf("id" to "2")),
      )
      .inOrder()
  }

  @Test
  fun `logs a warning once per type whose property encoding throws a LinkageError`() {
    val error = AbstractMethodError()
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = error
    fixture.searchSerializer.error = error
    val mapper = fixture.mapper

    repeat(3) {
      mapper.mapAndReadArguments(Detail("$it"))
      mapper.mapAndReadArguments(Search("$it"))
    }

    // Each entry's encoding threw, yet each type's warning was logged once.
    assertThat(fixture.detailSerializer.serialized)
      .containsExactly(Detail("0"), Detail("1"), Detail("2"))
    assertThat(fixture.searchSerializer.serialized)
      .containsExactly(Search("0"), Search("1"), Search("2"))
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        eq(error),
        any<String>(),
        eq(Detail::class.java.name),
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        eq(error),
        any<String>(),
        eq(Search::class.java.name),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `logs only the compatibility advice warning when a type's property encoding throws a LinkageError`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = AbstractMethodError()
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(Detail("1"))

    // A single warning, rather than the compatibility advice and the unencodable warning.
    val message = argumentCaptor<String>()
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        any<Throwable>(),
        message.capture(),
        eq(Detail::class.java.name),
      )
    assertThat(message.firstValue).contains(INCOMPATIBLE_KOTLINX_ADVICE)
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `attaches a cause-free LinkageError the JVM raises as is when property encoding throws it`() {
    // Its message only names the symbol that couldn't be linked, which helps diagnose the version.
    val error = NoSuchMethodError("com.example.Encoder.encodeInline(Lkotlin/Any;)V")
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = error
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(Detail("1"))

    val logged = argumentCaptor<Throwable>()
    verify(fixture.logger)
      .log(eq(SentryLevel.WARNING), logged.capture(), any<String>(), eq(Detail::class.java.name))
    assertThat(logged.firstValue).isSameInstanceAs(error)
  }

  @Test
  fun `attaches a JVM LinkageError's symbol but not its cause when property encoding throws it`() {
    // A class initializer that runs during encoding may put app data in the cause's message.
    val error =
      NoClassDefFoundError("Could not initialize class com.example.Config").apply {
        initCause(ExceptionInInitializerError(IllegalStateException("route id=ROUTE-SECRET")))
      }
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = error
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(Detail("1"))

    val logged = argumentCaptor<Throwable>()
    verify(fixture.logger)
      .log(eq(SentryLevel.WARNING), logged.capture(), any<String>(), eq(Detail::class.java.name))
    assertThat(logged.firstValue)
      .hasMessageThat()
      .isEqualTo("java.lang.NoClassDefFoundError: Could not initialize class com.example.Config")
    assertThat(logged.firstValue).hasCauseThat().isNull()
    assertThat(logged.firstValue.stackTrace).isEqualTo(error.stackTrace)
    assertThat(logged.firstValue.stackTraceToString()).doesNotContain("ROUTE-SECRET")
  }

  @Test
  fun `attaches only the class name of another LinkageError when a type's property encoding throws it`() {
    // E.g., an app serializer that throws its own LinkageError with route data in its message.
    val error = ExceptionInInitializerError("route id=ROUTE-SECRET")
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = error
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(Detail("1"))

    val logged = argumentCaptor<Throwable>()
    verify(fixture.logger)
      .log(eq(SentryLevel.WARNING), logged.capture(), any<String>(), eq(Detail::class.java.name))
    assertThat(logged.firstValue)
      .hasMessageThat()
      .isEqualTo("java.lang.ExceptionInInitializerError")
    assertThat(logged.firstValue.stackTraceToString()).doesNotContain("ROUTE-SECRET")
  }

  // Property encoding that throws other exceptions

  @Test
  fun `keeps the name and records only the encoding failure when a type's property encoding throws an exception`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = IllegalArgumentException("invalid id")
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped)
      .isEqualTo(SentryBackStackEntry("Detail", droppedBy(ArgumentDropReason.ENCODING_FAILED)))
  }

  @Test
  fun `logs a serializer-threw warning once per type whose property encoding throws an exception`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = IllegalArgumentException("invalid id")
    fixture.searchSerializer.error = IllegalArgumentException("invalid query")
    val mapper = fixture.mapper

    repeat(3) {
      mapper.mapAndReadArguments(Detail("$it"))
      mapper.mapAndReadArguments(Search("$it"))
    }

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(SERIALIZER_THREW) },
        eq(Detail::class.java.name),
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(SERIALIZER_THREW) },
        eq(Search::class.java.name),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `logs a serializer-threw warning once for an entry whose arguments are read and then written`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = IllegalArgumentException("invalid id")
    val arguments = fixture.mapper.map(Detail("1"))?.arguments!!
    arguments.size

    assertFailsWith<DropArguments> {
      (arguments as ArgumentSource).writeTo(TreeArgumentWriter(ArgumentBudget()))
    }

    assertThat(fixture.detailSerializer.serialized).containsExactly(Detail("1"), Detail("1"))
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(SERIALIZER_THREW) },
        eq(Detail::class.java.name),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `logs the serializer-threw warning separately from the LinkageError warning for the same type`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper
    fixture.detailSerializer.error = IllegalArgumentException("invalid id")
    repeat(2) { mapper.mapAndReadArguments(Detail("thrown-$it")) }
    val linkageError = AbstractMethodError()
    fixture.detailSerializer.error = linkageError

    repeat(2) { mapper.mapAndReadArguments(Detail("unlinkable-$it")) }

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(SERIALIZER_THREW) },
        eq(Detail::class.java.name),
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        eq(linkageError),
        argThat<String> { contains(INCOMPATIBLE_KOTLINX_ADVICE) },
        eq(Detail::class.java.name),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `propagates fatal errors thrown by property encoding when the arguments are read`() {
    val error = OutOfMemoryError()
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = error
    val mapper = fixture.mapper
    val arguments = mapper.map(Detail("1"))?.arguments

    val thrown = assertFailsWith<OutOfMemoryError> { arguments?.size }

    assertThat(thrown).isSameInstanceAs(error)
  }

  // Route data in property encoding warnings
  //
  // Serializer exceptions can contain route data in their messages and causes, so warnings for
  // them carry neither, nor the exception itself.

  @Test
  fun `keeps route data out of the serializer-threw warning`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error =
      IllegalArgumentException("Invalid ID $ROUTE_ID", IllegalStateException(ROUTE_ID))
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(Detail(ROUTE_ID))

    verifyWarningLoggedOnce(fixture, SERIALIZER_THREW, Detail::class.java)
    verifyNoRouteDataOrErrorLogged(fixture)
  }

  @Test
  fun `keeps route data out of the unsupported-properties warning`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error =
      SerializationException("Invalid ID $ROUTE_ID", IllegalStateException(ROUTE_ID))
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(Detail(ROUTE_ID))

    verifyWarningLoggedOnce(fixture, UNSUPPORTED_PROPERTIES, Detail::class.java)
    verifyNoRouteDataOrErrorLogged(fixture)
  }

  @Test
  fun `keeps route data out of the unencodable-values warning`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { otherSerializers[it] ?: fixture.serializers[it] }
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(WithRejecting("1", Rejecting(ROUTE_ID)))

    verifyWarningLoggedOnce(fixture, UNENCODABLE_VALUES, WithRejecting::class.java)
    verifyNoRouteDataOrErrorLogged(fixture)
  }

  @Test
  fun `keeps route data out of the over-limits warning`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(Detail(ROUTE_ID + overLimitId))

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(OVER_LIMITS) },
        eq(Detail::class.java.name),
        eq(ArgumentDropReason.CHARACTER_LIMIT.serializedValue),
        any<String>(),
      )
    verifyNoRouteDataOrErrorLogged(fixture)
  }

  // Serializer descriptors whose shape throws

  @Test
  fun `keeps the name and records the properties when an entry's descriptor's elementsCount throws`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = {
      ShapeThrowingDetailSerializer(elementsCountError = IllegalStateException("no count"))
    }
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail", mapOf("id" to "1")))
  }

  @Test
  fun `keeps the name and records only the encoding failure when an entry's descriptor's kind throws`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { ShapeThrowingDetailSerializer(kindError = IllegalStateException("no kind")) }
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped)
      .isEqualTo(SentryBackStackEntry("Detail", droppedBy(ArgumentDropReason.ENCODING_FAILED)))
  }

  @Test
  fun `keeps the name and records only the encoding failure when a property-less entry's descriptor's kind throws`() {
    // Without properties, whether the entry may still have arguments depends on its kind.
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { KindThrowingPropertyLessSerializer(IllegalStateException("no kind")) }
    val mapper = fixture.mapper

    val mapped = mapper.map(PropertyLess())

    assertThat(mapped)
      .isEqualTo(
        SentryBackStackEntry("PropertyLess", droppedBy(ArgumentDropReason.ENCODING_FAILED)),
      )
  }

  @Test
  fun `keeps the name and records only the encoding failure when a property-less entry's descriptor's kind throws a LinkageError`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { KindThrowingPropertyLessSerializer(NoClassDefFoundError()) }
    val mapper = fixture.mapper

    val mapped = mapper.map(PropertyLess())

    assertThat(mapped)
      .isEqualTo(
        SentryBackStackEntry("PropertyLess", droppedBy(ArgumentDropReason.ENCODING_FAILED)),
      )
  }

  @Test
  fun `propagates fatal errors thrown by a property-less entry's descriptor's kind`() {
    val error = OutOfMemoryError()
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { KindThrowingPropertyLessSerializer(error) }
    val mapper = fixture.mapper

    val thrown = assertFailsWith<OutOfMemoryError> { mapper.map(PropertyLess()) }

    assertThat(thrown).isSameInstanceAs(error)
  }

  @Test
  fun `logs a serializer-threw warning once for a type whose descriptor's kind throws`() {
    val error = IllegalStateException("no kind")
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { ShapeThrowingDetailSerializer(kindError = error) }
    val mapper = fixture.mapper

    repeat(3) { mapper.mapAndReadArguments(Detail("$it")) }

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(SERIALIZER_THREW) },
        eq(Detail::class.java.name),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `keeps the name and records the properties when an entry's descriptor's elementsCount throws a LinkageError`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { ShapeThrowingDetailSerializer(elementsCountError = NoClassDefFoundError()) }
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail", mapOf("id" to "1")))
  }

  @Test
  fun `keeps the name and records only the encoding failure when an entry's descriptor's kind throws a LinkageError`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { ShapeThrowingDetailSerializer(kindError = NoClassDefFoundError()) }
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped)
      .isEqualTo(SentryBackStackEntry("Detail", droppedBy(ArgumentDropReason.ENCODING_FAILED)))
  }

  @Test
  fun `propagates fatal errors thrown by an entry's descriptor's elementsCount`() {
    val error = OutOfMemoryError()
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { ShapeThrowingDetailSerializer(elementsCountError = error) }
    val mapper = fixture.mapper

    val thrown = assertFailsWith<OutOfMemoryError> { mapper.map(Detail("1")) }

    assertThat(thrown).isSameInstanceAs(error)
  }

  private fun verifyWarningLoggedOnce(
    fixture: KotlinxMapperFixture,
    warning: String,
    type: Class<*>,
  ) {
    verify(fixture.logger, times(1))
      .log(eq(SentryLevel.WARNING), argThat<String> { contains(warning) }, eq(type.name))
  }

  /**
   * Verifies that the over-limits warning was logged once for [type] and [reason], and returns its
   * description of the broken limit.
   */
  private fun overLimitsWarningLimit(
    fixture: KotlinxMapperFixture,
    type: Class<*>,
    reason: ArgumentDropReason,
  ): String {
    val limit = argumentCaptor<String>()
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(OVER_LIMITS) },
        eq(type.name),
        eq(reason.serializedValue),
        limit.capture(),
      )
    return limit.firstValue
  }

  /**
   * Checks every call to the fixture's logger, through any of its overloads, for [ROUTE_ID] and
   * attached errors.
   */
  private fun verifyNoRouteDataOrErrorLogged(fixture: KotlinxMapperFixture) {
    val logged =
      Mockito.mockingDetails(fixture.logger).invocations.flatMap { it.arguments.asList() }

    assertThat(logged).isNotEmpty()
    assertThat(logged.filter { it.toString().contains(ROUTE_ID) }).isEmpty()
    assertThat(logged.filterIsInstance<Throwable>()).isEmpty()
  }

  private companion object {
    /** Identifies a route, so it must not appear in logs. */
    const val ROUTE_ID = "private-route-id"

    const val OVER_LIMITS = "Nav3 dropped arguments for type %s"

    const val UNSUPPORTED_PROPERTIES = "values whose serializers fail partway aren't supported"

    const val UNENCODABLE_VALUES = "Recording them as [Unencodable]"

    const val SERIALIZER_THREW = "because a serializer threw"
  }
}
