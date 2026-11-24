package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryLevel
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.ArgumentSource
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import kotlin.test.assertFailsWith
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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

  /** Serializes [PropertyLess] with its own descriptor, except that reading its `kind` throws. */
  @OptIn(ExperimentalSerializationApi::class)
  object KindThrowingPropertyLessSerializer : KSerializer<PropertyLess> {
    private val delegate = PropertyLess.serializer().descriptor

    override val descriptor: SerialDescriptor =
      Mockito.mock(SerialDescriptor::class.java, delegatesTo<Any>(delegate)).apply {
        doThrow(IllegalStateException("no kind")).whenever(this).kind
      }

    override fun serialize(encoder: Encoder, value: PropertyLess) {
      encoder.encodeStructure(descriptor) {}
    }

    override fun deserialize(decoder: Decoder): PropertyLess = throw UnsupportedOperationException()
  }

  interface OpenOrigin

  /** Isn't `@Serializable`, so it can't be encoded as an [OpenOrigin] without a module. */
  class SearchOrigin : OpenOrigin

  @Serializable
  @SerialName("WithOpenPolymorphic")
  data class WithOpenPolymorphic(val id: String, @Polymorphic val origin: OpenOrigin)

  /** Encodes to its code, unless it [fails], in which case it's written partway and then throws. */
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

  @Serializable data class Point(val x: Int)

  @Serializable
  @SerialName("WithPointMapKeys")
  data class WithPointMapKeys(val id: String, val byPoint: Map<Point, Int>, val page: Int)

  /** Serializers of this suite's own types, which tests add to the fixture's lookup. */
  private val otherSerializers: Map<Class<*>, KSerializer<*>> =
    mapOf(
      WithOpenPolymorphic::class.java to WithOpenPolymorphic.serializer(),
      WithContextualAndFragile::class.java to WithContextualAndFragile.serializer(),
      WithPointMapKeys::class.java to WithPointMapKeys.serializer(),
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

  @Test
  fun `records a map with class keys as key and value entries beside the other arguments`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { otherSerializers[it] ?: fixture.serializers[it] }
    val mapper = fixture.mapper

    val mapped = mapper.map(WithPointMapKeys("1", mapOf(Point(1) to 2), page = 3))

    assertThat(mapped)
      .isEqualTo(
        SentryBackStackEntry(
          "WithPointMapKeys",
          mapOf(
            "id" to "1",
            "byPoint" to listOf(mapOf("key" to mapOf("x" to 1), "value" to 2)),
            "page" to 3,
          ),
        ),
      )
    verifyNoInteractions(fixture.logger)
  }

  // Property values that can't be encoded

  @Test
  fun `records unencodable property values as the Unencodable substitute and keeps the other arguments`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    val mapper = fixture.mapper

    val mapped = mapper.map(WithContextual("1", Unregistered("2")))

    assertThat(mapped)
      .isEqualTo(
        SentryBackStackEntry(
          "WithContextual",
          mapOf("id" to "1", "other" to KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE),
        ),
      )
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
  fun `keeps the name and records only the encoding failure when a type's properties can't be encoded`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { otherSerializers[it] ?: fixture.serializers[it] }
    val mapper = fixture.mapper

    val mapped = mapper.map(WithContextualAndFragile(null, Fragile(fails = true)))

    assertThat(mapped)
      .isEqualTo(
        SentryBackStackEntry(
          "WithContextualAndFragile",
          droppedBy(ArgumentDropReason.ENCODING_FAILED),
        ),
      )
  }

  @Test
  fun `keeps encoding a type's properties after they couldn't be encoded once`() {
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
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        any<String>(),
        eq(Search::class.java.name),
        eq(ArgumentDropReason.CHARACTER_LIMIT.serializedValue),
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
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(UNSUPPORTED_PROPERTIES) },
        eq(Detail::class.java.name),
      )
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
      .log(eq(SentryLevel.WARNING), eq(error), any<String>(), eq(Detail::class.java.name))
    verify(fixture.logger, times(1))
      .log(eq(SentryLevel.WARNING), eq(error), any<String>(), eq(Search::class.java.name))
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
  fun `attaches the LinkageError to the warning for a type whose property encoding throws it`() {
    // Unlike serializer exceptions, a LinkageError names the classes and members that couldn't be
    // linked or initialized, not route data.
    val error = NoClassDefFoundError("kotlinx/serialization/internal/Missing")
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = error
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(Detail("1"))

    val logged = argumentCaptor<Throwable>()
    verify(fixture.logger)
      .log(eq(SentryLevel.WARNING), logged.capture(), any<String>(), eq(Detail::class.java.name))
    assertThat(logged.firstValue).isSameInstanceAs(error)
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
  fun `keeps encoding a type's properties after its property encoding throws an exception once`() {
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error = IllegalArgumentException("invalid id")
    val mapper = fixture.mapper
    mapper.mapAndReadArguments(Detail("1"))
    fixture.detailSerializer.error = null

    val mapped = mapper.map(Detail("2"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail", mapOf("id" to "2")))
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
  fun `keeps route data from serializer exception messages and causes out of warnings`() {
    val detail = Detail("private-route-id")
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.detailSerializer.error =
      IllegalArgumentException("Invalid ID ${detail.id}", IllegalStateException(detail.id))
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(detail)

    val message = argumentCaptor<String>()
    verify(fixture.logger)
      .log(eq(SentryLevel.WARNING), message.capture(), eq(Detail::class.java.name))
    assertThat(message.firstValue).contains(SERIALIZER_THREW)
    assertThat(message.firstValue).doesNotContain(detail.id)
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
    fixture.lookUp = { KindThrowingPropertyLessSerializer }
    val mapper = fixture.mapper

    val mapped = mapper.map(PropertyLess())

    assertThat(mapped)
      .isEqualTo(
        SentryBackStackEntry("PropertyLess", droppedBy(ArgumentDropReason.ENCODING_FAILED)),
      )
  }

  @Test
  fun `logs a warning once for a type whose descriptor's kind throws`() {
    val error = IllegalStateException("no kind")
    val fixture = KotlinxMapperFixture.create(includeProperties = true)
    fixture.lookUp = { ShapeThrowingDetailSerializer(kindError = error) }
    val mapper = fixture.mapper

    repeat(3) { mapper.mapAndReadArguments(Detail("$it")) }

    verify(fixture.logger, times(1))
      .log(eq(SentryLevel.WARNING), any<String>(), eq(Detail::class.java.name))
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

  private companion object {
    const val UNSUPPORTED_PROPERTIES = "values whose serializers fail partway aren't supported"

    const val UNENCODABLE_VALUES = "Recording them as [Unencodable]"

    const val SERIALIZER_THREW = "because a serializer threw"
  }
}
