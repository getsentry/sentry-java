package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryLevel
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import java.lang.annotation.AnnotationFormatError
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFailsWith
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MetaSerializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions

@OptIn(ExperimentalSerializationApi::class)
@MetaSerializable
@Target(AnnotationTarget.CLASS)
private annotation class MetaSerializableRoute

@MetaSerializableRoute private data class MetaSerializableEntry(val id: String)

class KotlinxBackStackEntryMapperNamingTest {

  @Serializable data class WithoutSerialName(val id: String)

  @Serializable
  enum class Tab {
    FEED,
    @SerialName("tab.search") SEARCH,
  }

  @Serializable(with = CustomNameSerializer::class)
  @SerialName("IgnoredSerialName")
  internal class CustomSerialized

  internal object CustomNameSerializer : NamingOnlySerializer<CustomSerialized>() {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("custom.Name", PrimitiveKind.STRING)
  }

  @Serializable
  enum class TabWithBodies {
    FEED {
      override val icon = "feed"
    },
    SEARCH {
      override val icon = "search"
    };

    abstract val icon: String
  }

  data class NotSerializable(val id: String)

  enum class UnannotatedTab {
    FEED,
  }

  @Serializable data class Generic<T>(val value: T)

  @Serializable @SerialName("PrivateHome") private data object PrivateHome

  // A class whose serializer's initializer threw can't be initialized again for the rest of the
  // JVM's life: later lookups throw a NoClassDefFoundError instead. So each of these pairs is used
  // by exactly one test, and that test only sees the initializer's own error on its first run in a
  // JVM.

  /** Used by exactly one test, as its serializer can't be initialized. */
  @Serializable(with = UninitializableSerializerA::class) internal class UninitializableSerializedA

  internal object UninitializableSerializerA : NamingOnlySerializer<UninitializableSerializedA>() {
    init {
      if (System.nanoTime() != 0L) error("serializer A init failed")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("uninitializable.A", PrimitiveKind.STRING)
  }

  /** Used by exactly one test, as its serializer can't be initialized. */
  @Serializable(with = UninitializableSerializerB::class) internal class UninitializableSerializedB

  internal object UninitializableSerializerB : NamingOnlySerializer<UninitializableSerializedB>() {
    init {
      if (System.nanoTime() != 0L) error("serializer B init failed")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("uninitializable.B", PrimitiveKind.STRING)
  }

  /** Used by exactly one test, as its serializer can't be initialized. */
  @Serializable(with = UninitializableSerializerC::class) private object PrivateUninitializable

  private object UninitializableSerializerC : NamingOnlySerializer<PrivateUninitializable>() {
    init {
      if (System.nanoTime() != 0L) error("serializer C init failed")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("uninitializable.C", PrimitiveKind.STRING)
  }

  /**
   * Used by exactly one test. Unlike an exception, a fatal error from the initializer isn't
   * wrapped, but the serializer still can't be initialized again.
   */
  @Serializable(with = FatallyUninitializableSerializer::class)
  internal class FatallyUninitializableSerialized

  internal object FatallyUninitializableSerializer :
    NamingOnlySerializer<FatallyUninitializableSerialized>() {
    init {
      if (System.nanoTime() != 0L) throw OutOfMemoryError("serializer init failed fatally")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("fatallyUninitializable", PrimitiveKind.STRING)
  }

  /**
   * A serializer class, unlike an object, is constructed on each lookup, so one construction can
   * fail while later ones succeed.
   */
  @Serializable(with = FatalOnceSerializer::class) internal class FatalOnceSerialized

  /**
   * Fails its next construction fatally once a test sets [failNextConstruction], rather than its
   * first construction in the JVM, so that the test passes when run again in the same JVM.
   */
  internal class FatalOnceSerializer : NamingOnlySerializer<FatalOnceSerialized>() {
    init {
      if (failNextConstruction.getAndSet(false)) {
        throw OutOfMemoryError("serializer construction failed fatally")
      }
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("fatalOnce", PrimitiveKind.STRING)

    companion object {
      val failNextConstruction = AtomicBoolean()
    }
  }

  @Serializable(with = DescriptorThrowsIllegalStateSerializer::class)
  internal class DescriptorThrowsIllegalState

  internal object DescriptorThrowsIllegalStateSerializer :
    NamingOnlySerializer<DescriptorThrowsIllegalState>() {
    val error = IllegalStateException("descriptor failed")

    override val descriptor: SerialDescriptor
      get() = throw error
  }

  @Serializable(with = DescriptorThrowsAbstractMethodErrorSerializer::class)
  internal class DescriptorThrowsAbstractMethodError

  internal object DescriptorThrowsAbstractMethodErrorSerializer :
    NamingOnlySerializer<DescriptorThrowsAbstractMethodError>() {
    override val descriptor: SerialDescriptor
      get() = throw AbstractMethodError("descriptor not implemented")
  }

  @Serializable(with = DescriptorThrowsNoClassDefFoundErrorSerializer::class)
  internal class DescriptorThrowsNoClassDefFoundError

  internal object DescriptorThrowsNoClassDefFoundErrorSerializer :
    NamingOnlySerializer<DescriptorThrowsNoClassDefFoundError>() {
    val error = NoClassDefFoundError("com/example/MissingDescriptorClass")

    override val descriptor: SerialDescriptor
      get() = throw error
  }

  // Naming

  @Test
  fun `names entries after their SerialName`() {
    val fixture = KotlinxMapperFixture.create()
    val mapper = fixture.mapper

    val mapped = listOf(mapper.map(Detail("1")), mapper.map(Search("shoes")))

    assertThat(mapped)
      .containsExactly(SentryBackStackEntry("Detail"), SentryBackStackEntry("Search"))
      .inOrder()
  }

  @Test
  fun `names entries without @SerialName after their fully qualified class name`() {
    val fixture = KotlinxMapperFixture.create(useDefaultLookUp = true)
    val mapper = fixture.mapper

    val mapped = mapper.map(WithoutSerialName("1"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("$TEST_CLASS.WithoutSerialName"))
  }

  @Test
  fun `names constants of @Serializable enums after their enum`() {
    val fixture = KotlinxMapperFixture.create(useDefaultLookUp = true)
    val mapper = fixture.mapper

    val mapped = mapper.map(Tab.FEED)

    assertThat(mapped).isEqualTo(SentryBackStackEntry("$TEST_CLASS.Tab"))
  }

  @Test
  fun `names enum constants that have a @SerialName after their enum`() {
    val fixture = KotlinxMapperFixture.create(useDefaultLookUp = true)
    val mapper = fixture.mapper

    val mapped = mapper.map(Tab.SEARCH)

    assertThat(mapped).isEqualTo(SentryBackStackEntry("$TEST_CLASS.Tab"))
  }

  @Test
  fun `names constants of @Serializable enums that have bodies after their enum`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { if (it == TabWithBodies::class.java) TabWithBodies.serializer() else null }
    val mapper = fixture.mapper

    val mapped = mapper.map(TabWithBodies.SEARCH)

    assertThat(mapped).isEqualTo(SentryBackStackEntry("$TEST_CLASS.TabWithBodies"))
  }

  @Test
  fun `names entries of a class with a custom serializer after its descriptor, ignoring the class's @SerialName`() {
    val fixture = KotlinxMapperFixture.create(useDefaultLookUp = true)
    val mapper = fixture.mapper

    val mapped = mapper.map(CustomSerialized())

    assertThat(mapped).isEqualTo(SentryBackStackEntry("custom.Name"))
  }

  @Test
  fun `names entries of a private @Serializable object after its SerialName`() {
    val fixture = KotlinxMapperFixture.create(useDefaultLookUp = true)
    val mapper = fixture.mapper

    val mapped = mapper.map(PrivateHome)

    assertThat(mapped).isEqualTo(SentryBackStackEntry("PrivateHome"))
    verifyNoInteractions(fixture.logger)
  }

  @Test
  fun `returns null for entries that aren't @Serializable`() {
    val fixture = KotlinxMapperFixture.create()
    val mapper = fixture.mapper

    val mapped = mapper.map(NotSerializable("1"))

    assertThat(mapped).isNull()
  }

  @Test
  fun `returns null for entries that aren't @Serializable even if the lookup has a serializer`() {
    // kotlinx serialization has serializers for built-in types and enums that aren't @Serializable.
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { String.serializer() }
    val mapper = fixture.mapper

    val mapped =
      listOf(
        mapper.map("home"),
        mapper.map(42),
        mapper.map(UnannotatedTab.FEED),
        mapper.map(NotSerializable("1")),
      )

    assertThat(mapped).containsExactly(null, null, null, null)
  }

  @Test
  fun `returns null for entries of a class marked with a @MetaSerializable annotation`() {
    val fixture = KotlinxMapperFixture.create(useDefaultLookUp = true)
    val mapper = fixture.mapper

    val mapped = mapper.map(MetaSerializableEntry("1"))

    assertThat(mapped).isNull()
  }

  // Name lookup caching and logging

  @Test
  fun `looks up each @Serializable type once, including failed lookups`() {
    val fixture = KotlinxMapperFixture.create()
    val mapper = fixture.mapper

    repeat(3) {
      mapper.map(Home)
      mapper.map(Detail("$it"))
      mapper.map(Generic("$it"))
    }

    assertThat(fixture.lookedUpTypes)
      .containsExactly(Home::class.java, Detail::class.java, Generic::class.java)
  }

  @Test
  fun `never looks up types that aren't @Serializable`() {
    val fixture = KotlinxMapperFixture.create()
    val mapper = fixture.mapper

    repeat(3) {
      mapper.map(NotSerializable("$it"))
      mapper.map("home")
      mapper.map(42)
      mapper.map(UnannotatedTab.FEED)
    }

    assertThat(fixture.lookedUpTypes).isEmpty()
  }

  @Test
  fun `looks up an enum that has bodies once for all its constants`() {
    val fixture = KotlinxMapperFixture.create()
    val mapper = fixture.mapper

    mapper.map(TabWithBodies.FEED)
    mapper.map(TabWithBodies.SEARCH)

    assertThat(fixture.lookedUpTypes).containsExactly(TabWithBodies::class.java)
  }

  @Test
  fun `logs a warning once per type that isn't @Serializable`() {
    val fixture = KotlinxMapperFixture.create()
    val mapper = fixture.mapper

    repeat(3) {
      mapper.map(NotSerializable("$it"))
      mapper.map("home")
      mapper.map(UnannotatedTab.FEED)
      mapper.map(Home)
    }

    verifyNotSerializableLoggedOnce(fixture, NotSerializable::class.java)
    verifyNotSerializableLoggedOnce(fixture, String::class.java)
    verifyNotSerializableLoggedOnce(fixture, UnannotatedTab::class.java)
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `logs a warning once per @Serializable type whose serializer can't be found`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { if (it == Detail::class.java) null else fixture.serializers[it] }
    val mapper = fixture.mapper

    repeat(3) {
      mapper.map(Detail("$it"))
      mapper.map(Home)
    }

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        isNull<Throwable>(),
        any<String>(),
        eq(Detail::class.java.name),
        eq(SERIALIZER_NOT_FOUND),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `advises making the type @Serializable when a type that isn't @Serializable can't be named`() {
    val fixture = KotlinxMapperFixture.create()
    val mapper = fixture.mapper

    mapper.map(NotSerializable("1"))

    val message = argumentCaptor<String>()
    verify(fixture.logger)
      .log(eq(SentryLevel.WARNING), message.capture(), eq(NotSerializable::class.java.name))
    assertThat(message.firstValue).contains("Add @Serializable")
  }

  @Test
  fun `advises seeing the docs when a @Serializable type's serializer can't be found`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { null }
    val mapper = fixture.mapper

    mapper.map(Detail("1"))

    val message = argumentCaptor<String>()
    verify(fixture.logger)
      .log(
        eq(SentryLevel.WARNING),
        isNull<Throwable>(),
        message.capture(),
        eq(Detail::class.java.name),
        eq(SERIALIZER_NOT_FOUND),
      )
    assertThat(message.firstValue).contains("See BackStackEntryMapper.SerialName's documentation")
    assertThat(message.firstValue).doesNotContain("Add @Serializable")
  }

  @Test
  fun `advises seeing the attached error without compatibility advice when a type's name lookup throws an exception`() {
    val error = IllegalStateException("lookup failed")
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw error }
    val mapper = fixture.mapper

    mapper.map(Detail("1"))

    val message = argumentCaptor<String>()
    verify(fixture.logger)
      .log(
        eq(SentryLevel.WARNING),
        eq(error),
        message.capture(),
        eq(Detail::class.java.name),
        eq(LOOK_UP_ERROR),
      )
    assertThat(message.firstValue).contains("See the attached error")
    assertThat(message.firstValue).doesNotContain(INCOMPATIBLE_KOTLINX_ADVICE)
  }

  @Test
  fun `advises checking kotlinx compatibility when a type's name lookup throws a LinkageError`() {
    val error = NoSuchMethodError()
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw error }
    val mapper = fixture.mapper

    mapper.map(Detail("1"))

    val message = argumentCaptor<String>()
    verify(fixture.logger)
      .log(
        eq(SentryLevel.WARNING),
        eq(error),
        message.capture(),
        eq(Detail::class.java.name),
        eq(LOOK_UP_ERROR),
      )
    assertThat(message.firstValue).contains("See the attached error")
    assertThat(message.firstValue).contains(INCOMPATIBLE_KOTLINX_ADVICE)
  }

  // @Serializable annotation checks that throw

  @Test
  fun `returns null and logs the error with compatibility advice once for a type whose annotation check throws a LinkageError`() {
    val error = NoClassDefFoundError("com/example/MissingAnnotation")
    val fixture = KotlinxMapperFixture.create(isAnnotatedSerializable = { throw error })
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(Detail("$it")) }

    assertThat(mapped).containsExactly(null, null, null)
    val message = argumentCaptor<String>()
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        eq(error),
        message.capture(),
        eq(Detail::class.java.name),
        eq(LOOK_UP_ERROR),
      )
    assertThat(message.firstValue).contains(INCOMPATIBLE_KOTLINX_ADVICE)
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `returns null and logs the error without compatibility advice once for a type whose annotation check throws an exception`() {
    val error = AnnotationFormatError("malformed annotation")
    val fixture = KotlinxMapperFixture.create(isAnnotatedSerializable = { throw error })
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(Detail("$it")) }

    assertThat(mapped).containsExactly(null, null, null)
    val message = argumentCaptor<String>()
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        eq(error),
        message.capture(),
        eq(Detail::class.java.name),
        eq(LOOK_UP_ERROR),
      )
    assertThat(message.firstValue).doesNotContain(INCOMPATIBLE_KOTLINX_ADVICE)
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `doesn't check or look up a type again after its annotation check throws`() {
    val checkedTypes = mutableListOf<Class<*>>()
    val fixture =
      KotlinxMapperFixture.create(
        isAnnotatedSerializable = {
          checkedTypes += it
          throw NoClassDefFoundError()
        },
      )
    val mapper = fixture.mapper

    repeat(3) { mapper.map(Detail("$it")) }

    assertThat(checkedTypes).containsExactly(Detail::class.java)
    assertThat(fixture.lookedUpTypes).isEmpty()
  }

  @Test
  fun `keeps naming other types after a type's annotation check throws`() {
    val fixture =
      KotlinxMapperFixture.create(
        isAnnotatedSerializable = {
          if (it == Detail::class.java) throw AnnotationFormatError("malformed annotation")
          true
        },
      )
    val mapper = fixture.mapper

    val mapped = listOf(mapper.map(Detail("1")), mapper.map(Home))

    assertThat(mapped).containsExactly(null, SentryBackStackEntry("Home")).inOrder()
  }

  @Test
  fun `propagates fatal errors an annotation check throws`() {
    val error = OutOfMemoryError()
    val fixture = KotlinxMapperFixture.create(isAnnotatedSerializable = { throw error })
    val mapper = fixture.mapper

    val thrown = assertFailsWith<OutOfMemoryError> { mapper.map(Detail("1")) }

    assertThat(thrown).isSameInstanceAs(error)
  }

  // Serializer lookup errors that only affect one type

  @Test
  fun `returns null and logs the error once for a type whose name lookup throws a LinkageError`() {
    val error = NoSuchMethodError()
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw error }
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(Detail("$it")) }

    assertThat(mapped).containsExactly(null, null, null)
    verifyLookUpErrorLoggedOnce(fixture, error)
  }

  @Test
  fun `doesn't look up a type again after its name lookup throws a LinkageError`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw NoSuchMethodError() }
    val mapper = fixture.mapper

    repeat(3) { mapper.map(Detail("$it")) }

    assertThat(fixture.lookedUpTypes).containsExactly(Detail::class.java)
  }

  @Test
  fun `keeps naming other types after a type's name lookup throws a LinkageError`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = {
      if (it == Detail::class.java) throw NoSuchMethodError() else fixture.serializers[it]
    }
    val mapper = fixture.mapper

    val mapped = listOf(mapper.map(Detail("1")), mapper.map(Home), mapper.map(Search("shoes")))

    assertThat(mapped)
      .containsExactly(null, SentryBackStackEntry("Home"), SentryBackStackEntry("Search"))
      .inOrder()
  }

  @Test
  fun `returns null and logs the error once for a type whose name lookup throws ReflectiveOperationException`() {
    val error = NoSuchMethodException("serializer")
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw error }
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(Detail("$it")) }

    assertThat(mapped).containsExactly(null, null, null)
    verifyLookUpErrorLoggedOnce(fixture, error)
  }

  @Test
  fun `returns null and logs the error once for a type whose name lookup throws an unchecked exception`() {
    val error = TypeNotPresentException("com.example.RemovedSerializer", null)
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw error }
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(Detail("$it")) }

    assertThat(mapped).containsExactly(null, null, null)
    verifyLookUpErrorLoggedOnce(fixture, error)
  }

  @Test
  fun `doesn't look up a type again after its name lookup throws an unchecked exception`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { error("lookup failed") }
    val mapper = fixture.mapper

    repeat(3) { mapper.map(Detail("$it")) }

    assertThat(fixture.lookedUpTypes).containsExactly(Detail::class.java)
  }

  @Test
  fun `keeps naming other types after a type's name lookup throws an unchecked exception`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = {
      if (it == Detail::class.java) error("lookup failed") else fixture.serializers[it]
    }
    val mapper = fixture.mapper

    val mapped = listOf(mapper.map(Detail("1")), mapper.map(Home))

    assertThat(mapped).containsExactly(null, SentryBackStackEntry("Home")).inOrder()
  }

  @Test
  fun `restores the interrupt status when a type's name lookup throws InterruptedException`() {
    val error = InterruptedException()
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw error }
    val mapper = fixture.mapper

    try {
      assertThat(mapper.map(Detail("1"))).isNull()
      assertThat(Thread.currentThread().isInterrupted).isTrue()
      verifyLookUpErrorLoggedOnce(fixture, error)
    } finally {
      Thread.interrupted()
    }
  }

  @Test
  fun `propagates fatal errors a name lookup throws`() {
    val error = OutOfMemoryError()
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw error }
    val mapper = fixture.mapper

    val thrown = assertFailsWith<OutOfMemoryError> { mapper.map(Detail("1")) }

    assertThat(thrown).isSameInstanceAs(error)
  }

  // Serializer lookup errors wrapped in an InvocationTargetException

  @Test
  fun `returns null and logs the cause once for a type whose name lookup throws a wrapped exception`() {
    val cause = IllegalStateException("serializer() failed")
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw InvocationTargetException(cause) }
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(Detail("$it")) }

    assertThat(mapped).containsExactly(null, null, null)
    verifyLookUpErrorLoggedOnce(fixture, cause)
  }

  @Test
  fun `returns null and logs the cause once when a name lookup throws a wrapped ExceptionInInitializerError`() {
    val cause = ExceptionInInitializerError(IllegalStateException("serializer init failed"))
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw InvocationTargetException(cause) }
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(Detail("$it")) }

    assertThat(mapped).containsExactly(null, null, null)
    verifyLookUpErrorLoggedOnce(fixture, cause)
  }

  @Test
  fun `keeps naming other types after a type's name lookup throws a wrapped LinkageError`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = {
      if (it == Detail::class.java) throw InvocationTargetException(NoSuchMethodError())
      else fixture.serializers[it]
    }
    val mapper = fixture.mapper

    val mapped = listOf(mapper.map(Detail("1")), mapper.map(Home))

    assertThat(mapped).containsExactly(null, SentryBackStackEntry("Home")).inOrder()
  }

  @Test
  fun `logs the cause of a wrapped LinkageError, with compatibility advice, when a name lookup throws one`() {
    val cause = NoSuchMethodError("serializer")
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw InvocationTargetException(cause) }
    val mapper = fixture.mapper

    mapper.map(Detail("1"))

    val message = argumentCaptor<String>()
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        eq(cause),
        message.capture(),
        eq(Detail::class.java.name),
        eq(LOOK_UP_ERROR),
      )
    assertThat(message.firstValue).contains(INCOMPATIBLE_KOTLINX_ADVICE)
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `returns null and logs the InvocationTargetException once when a name lookup throws one without a cause`() {
    val error = InvocationTargetException(null)
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw error }
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(Detail("$it")) }

    assertThat(mapped).containsExactly(null, null, null)
    verifyLookUpErrorLoggedOnce(fixture, error)
  }

  @Test
  fun `restores the interrupt status when a type's name lookup throws a wrapped InterruptedException`() {
    val error = InterruptedException()
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw InvocationTargetException(error) }
    val mapper = fixture.mapper

    try {
      assertThat(mapper.map(Detail("1"))).isNull()
      assertThat(Thread.currentThread().isInterrupted).isTrue()
      verifyLookUpErrorLoggedOnce(fixture, error)
    } finally {
      Thread.interrupted()
    }
  }

  @Test
  fun `propagates fatal errors a name lookup throws wrapped in an InvocationTargetException`() {
    val error = OutOfMemoryError()
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw InvocationTargetException(error) }
    val mapper = fixture.mapper

    val thrown = assertFailsWith<OutOfMemoryError> { mapper.map(Detail("1")) }

    assertThat(thrown).isSameInstanceAs(error)
  }

  // Serializer descriptors that throw

  @Test
  fun `returns null without looking up again for a type whose serializer's descriptor throws`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { DescriptorThrowsIllegalStateSerializer }
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(DescriptorThrowsIllegalState()) }

    assertThat(mapped).containsExactly(null, null, null)
    assertThat(fixture.lookedUpTypes).containsExactly(DescriptorThrowsIllegalState::class.java)
  }

  @Test
  fun `logs the error once for a type whose serializer's descriptor throws`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { DescriptorThrowsIllegalStateSerializer }
    val mapper = fixture.mapper

    repeat(3) { mapper.map(DescriptorThrowsIllegalState()) }

    verifyLookUpErrorLoggedOnce(
      fixture,
      DescriptorThrowsIllegalStateSerializer.error,
      DescriptorThrowsIllegalState::class.java,
    )
  }

  @Test
  fun `keeps naming other types after a type's serializer's descriptor throws AbstractMethodError`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = {
      if (it == DescriptorThrowsAbstractMethodError::class.java) {
        DescriptorThrowsAbstractMethodErrorSerializer
      } else {
        fixture.serializers[it]
      }
    }
    val mapper = fixture.mapper

    val mapped = listOf(mapper.map(DescriptorThrowsAbstractMethodError()), mapper.map(Home))

    assertThat(mapped).containsExactly(null, SentryBackStackEntry("Home")).inOrder()
  }

  @Test
  fun `returns null and logs the error once for a type whose serializer's descriptor throws NoClassDefFoundError`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { DescriptorThrowsNoClassDefFoundErrorSerializer }
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(DescriptorThrowsNoClassDefFoundError()) }

    assertThat(mapped).containsExactly(null, null, null)
    verifyLookUpErrorLoggedOnce(
      fixture,
      DescriptorThrowsNoClassDefFoundErrorSerializer.error,
      DescriptorThrowsNoClassDefFoundError::class.java,
    )
  }

  @Test
  fun `propagates fatal errors thrown by a serializer's descriptor`() {
    val error = OutOfMemoryError()
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = {
      object : NamingOnlySerializer<Any>() {
        override val descriptor: SerialDescriptor
          get() = throw error
      }
    }
    val mapper = fixture.mapper

    val thrown = assertFailsWith<OutOfMemoryError> { mapper.map(Detail("1")) }

    assertThat(thrown).isSameInstanceAs(error)
  }

  // Serializer descriptors whose shape throws

  @Test
  fun `names an entry after its SerialName without reading its descriptor's shape when properties are excluded`() {
    val serializer = ShapeThrowingDetailSerializer()
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { serializer }
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail"))
    assertThat(serializer.shapeReads).isEqualTo(0)
  }

  // Integration: serializer initializers that throw through real reflection
  //
  // Unlike the tests above, these look up serializers with the mapper's default lookup,
  // reflectiveSerializerOrNull, either directly or wrapped to record each lookup, so its real
  // reflection throws the errors the mapper must handle.

  @Test
  fun `returns null without looking up again for a type whose serializer's initializer throws`() {
    val fixture = KotlinxMapperFixture.create()
    // Wraps the default lookup, rather than using it directly, so that lookedUpTypes records each
    // lookup.
    fixture.lookUp = { reflectiveSerializerOrNull(it) }
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(UninitializableSerializedA()) }

    assertThat(mapped).containsExactly(null, null, null)
    assertThat(fixture.lookedUpTypes).containsExactly(UninitializableSerializedA::class.java)
  }

  @Test
  fun `logs the initializer error once for a type whose serializer's initializer throws`() {
    val fixture = KotlinxMapperFixture.create(useDefaultLookUp = true)
    val mapper = fixture.mapper

    repeat(3) { mapper.map(UninitializableSerializedB()) }

    val logged = argumentCaptor<Throwable>()
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        logged.capture(),
        any<String>(),
        eq(UninitializableSerializedB::class.java.name),
        eq(LOOK_UP_ERROR),
      )
    assertThat(logged.firstValue).isInstanceOf(ExceptionInInitializerError::class.java)
    assertThat(logged.firstValue)
      .hasCauseThat()
      .hasMessageThat()
      .isEqualTo("serializer B init failed")
  }

  @Test
  fun `logs the initializer error once for a private object whose serializer's initializer throws`() {
    val fixture = KotlinxMapperFixture.create(useDefaultLookUp = true)
    val mapper = fixture.mapper

    val mapped = List(3) { mapper.map(PrivateUninitializable) }

    assertThat(mapped).containsExactly(null, null, null)
    val logged = argumentCaptor<Throwable>()
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        logged.capture(),
        any<String>(),
        eq(PrivateUninitializable::class.java.name),
        eq(LOOK_UP_ERROR),
      )
    assertThat(logged.firstValue).isInstanceOf(ExceptionInInitializerError::class.java)
    assertThat(logged.firstValue)
      .hasCauseThat()
      .hasMessageThat()
      .isEqualTo("serializer C init failed")
  }

  @Test
  fun `propagates a fatal error from a type's serializer initializer`() {
    val fixture = KotlinxMapperFixture.create(useDefaultLookUp = true)
    val mapper = fixture.mapper

    val thrown =
      assertFailsWith<OutOfMemoryError> { mapper.map(FatallyUninitializableSerialized()) }

    assertThat(thrown).hasMessageThat().isEqualTo("serializer init failed fatally")
  }

  @Test
  fun `names a type after its serializer lookup previously threw a fatal error`() {
    val fixture = KotlinxMapperFixture.create(useDefaultLookUp = true)
    val mapper = fixture.mapper
    FatalOnceSerializer.failNextConstruction.set(true)
    assertFailsWith<OutOfMemoryError> { mapper.map(FatalOnceSerialized()) }

    val mapped = mapper.map(FatalOnceSerialized())

    assertThat(mapped).isEqualTo(SentryBackStackEntry("fatalOnce"))
  }

  private fun verifyLookUpErrorLoggedOnce(
    fixture: KotlinxMapperFixture,
    error: Throwable,
    type: Class<*> = Detail::class.java,
  ) {
    verify(fixture.logger, times(1))
      .log(eq(SentryLevel.WARNING), eq(error), any<String>(), eq(type.name), eq(LOOK_UP_ERROR))
  }

  private fun verifyNotSerializableLoggedOnce(fixture: KotlinxMapperFixture, type: Class<*>) {
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains(NOT_SERIALIZABLE) },
        eq(type.name),
      )
  }

  private companion object {
    val TEST_CLASS: String = KotlinxBackStackEntryMapperNamingTest::class.java.name

    const val NOT_SERIALIZABLE = "because the type is not @Serializable"

    const val SERIALIZER_NOT_FOUND =
      "no serializer was found (the type may be generic or require R8 keep rules)"

    const val LOOK_UP_ERROR = "reading its annotations, serializer, or descriptor failed"
  }
}
