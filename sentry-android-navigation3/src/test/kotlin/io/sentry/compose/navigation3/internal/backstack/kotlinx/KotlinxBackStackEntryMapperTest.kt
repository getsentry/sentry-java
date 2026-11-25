package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.collectWithReason
import io.sentry.compose.navigation3.internal.backstack.kotlinx.FakeCompatibilityProbe.Inconclusive
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.LOOK_UP_FAILED
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.PROBE_THREW
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.SERIALIZER_NOT_FOUND
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason
import java.lang.annotation.AnnotationFormatError
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertFailsWith
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MetaSerializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions

@OptIn(ExperimentalSerializationApi::class)
@MetaSerializable
@Target(AnnotationTarget.CLASS)
private annotation class MetaSerializableRoute

@MetaSerializableRoute private data class MetaSerializableEntry(val id: String)

class KotlinxBackStackEntryMapperTest {

  @Serializable
  @SerialName("TabWithBodies")
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

  // Construction

  @Test
  fun `logs nothing when created and the compatibility probe finds kotlinx serialization compatible`() {
    val logger = mock<ILogger>()

    KotlinxBackStackEntryMapper(probe = FakeCompatibilityProbe(), logger = { logger })

    verifyNoInteractions(logger)
  }

  @Test
  fun `fails creation with the LinkageError the compatibility probe returns`() {
    val error = NoSuchMethodError("serializerOrNull")
    val probe = FakeCompatibilityProbe(linkageError = error)

    val thrown =
      assertFailsWith<NoSuchMethodError> {
        KotlinxBackStackEntryMapper(probe = probe, logger = { mock() })
      }

    assertThat(thrown).isSameInstanceAs(error)
  }

  @Test
  fun `logs nothing when the compatibility probe returns a LinkageError`() {
    // BuiltInBackStackEntryMappers logs the error it catches, so the mapper doesn't log it too.
    val logger = mock<ILogger>()
    val probe = FakeCompatibilityProbe(linkageError = NoSuchMethodError())

    assertFailsWith<NoSuchMethodError> {
      KotlinxBackStackEntryMapper(probe = probe, logger = { logger })
    }

    verifyNoInteractions(logger)
  }

  @Test
  fun `logs the compatibility probe's error at debug level when the probe is inconclusive`() {
    val error = IllegalStateException("probe failed")
    val logger = mock<ILogger>()
    val probe = FakeCompatibilityProbe(inconclusive = Inconclusive(error))

    KotlinxBackStackEntryMapper(probe = probe, logger = { logger })

    verify(logger).log(eq(SentryLevel.DEBUG), eq(error), any<String>(), eq(PROBE_THREW))
  }

  @Test
  fun `names entries when the compatibility probe is inconclusive`() {
    val probe = FakeCompatibilityProbe(inconclusive = Inconclusive(IllegalStateException()))
    val fixture = Fixture.create(probe = probe)
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail"))
  }

  // Naming

  @Test
  fun `names entries after their serializer's serial name`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    val mapped = listOf(mapper.map(Detail("1")), mapper.map(Search("shoes")))

    assertThat(mapped)
      .containsExactly(SentryBackStackEntry("Detail"), SentryBackStackEntry("Search"))
      .inOrder()
  }

  @Test
  fun `names constants of an enum with constant bodies after the enum's serializer`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    val mapped = mapper.map(TabWithBodies.SEARCH)

    assertThat(mapped).isEqualTo(SentryBackStackEntry("TabWithBodies"))
  }

  // Annotation check

  @Test
  fun `returns null for entries that aren't annotated @Serializable, even if the serializer lookup has a serializer`() {
    // kotlinx.serialization has serializers for built-in types and enums that aren't @Serializable.
    val fixture = Fixture.create(lookUpSerializer = { String.serializer() })
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
  fun `returns null for entries of a class annotated with a @MetaSerializable annotation`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    val mapped = mapper.map(MetaSerializableEntry("1"))

    assertThat(mapped).isNull()
  }

  @Test
  fun `doesn't look up serializers for entries that aren't annotated @Serializable`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    mapper.map(NotSerializable("1"))
    mapper.map("home")
    mapper.map(42)
    mapper.map(UnannotatedTab.FEED)

    assertThat(fixture.lookedUpTypes).isEmpty()
  }

  @Test
  fun `returns null and logs the error when the annotation check throws an exception`() {
    val error = AnnotationFormatError("malformed annotation")
    val fixture = Fixture.create(isAnnotatedSerializable = { throw error })
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isNull()
    verifyLookUpFailureLogged(fixture.logger, error)
  }

  @Test
  fun `returns null and logs the error when the annotation check throws a LinkageError`() {
    val error = NoClassDefFoundError("com/example/MissingAnnotation")
    val fixture = Fixture.create(isAnnotatedSerializable = { throw error })
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isNull()
    verifyLookUpFailureLogged(fixture.logger, error)
  }

  @Test
  fun `restores the interrupt flag when the annotation check throws InterruptedException`() {
    val fixture = Fixture.create(isAnnotatedSerializable = { throw InterruptedException() })
    val mapper = fixture.mapper

    try {
      val mapped = mapper.map(Detail("1"))

      assertThat(mapped).isNull()
      assertThat(Thread.currentThread().isInterrupted).isTrue()
    } finally {
      Thread.interrupted()
    }
  }

  @Test
  fun `keeps naming other types after the annotation check throws for one type`() {
    val fixture =
      Fixture.create(
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
  fun `propagates a fatal error the annotation check throws`() {
    val error = OutOfMemoryError()
    val fixture = Fixture.create(isAnnotatedSerializable = { throw error })
    val mapper = fixture.mapper

    val thrown = assertFailsWith<OutOfMemoryError> { mapper.map(Detail("1")) }

    assertThat(thrown).isSameInstanceAs(error)
  }

  // Generic types

  @Test
  fun `returns null for entries of a generic @Serializable type`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    val mapped = mapper.map(Generic("1"))

    assertThat(mapped).isNull()
  }

  @Test
  fun `doesn't look up serializers for generic @Serializable types`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    mapper.map(Generic("1"))

    assertThat(fixture.lookedUpTypes).isEmpty()
  }

  // Serializer lookup failures

  @Test
  fun `returns null and logs that no serializer was found when the serializer lookup finds none`() {
    val fixture = Fixture.create(lookUpSerializer = { null })
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isNull()
    verifySerializerNotFoundLogged(fixture.logger)
  }

  @Test
  fun `returns null and logs the error when the serializer lookup throws a LinkageError`() {
    val error = NoSuchMethodError("kotlinx.serialization.KSerializer.getDescriptor()")
    val fixture = Fixture.create(lookUpSerializer = { throw error })
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isNull()
    verifyLookUpFailureLogged(fixture.logger, error)
  }

  @Test
  fun `returns null and logs the error when the serializer lookup throws a ReflectiveOperationException`() {
    val error = NoSuchMethodException("serializer")
    val fixture = Fixture.create(lookUpSerializer = { throw error })
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isNull()
    verifyLookUpFailureLogged(fixture.logger, error)
  }

  @Test
  fun `returns null and logs the error when the serializer lookup throws an unchecked exception`() {
    val error = TypeNotPresentException("com.example.RemovedSerializer", null)
    val fixture = Fixture.create(lookUpSerializer = { throw error })
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isNull()
    verifyLookUpFailureLogged(fixture.logger, error)
  }

  @Test
  fun `returns null and logs the cause when the serializer lookup throws a wrapped exception`() {
    val cause = IllegalStateException("serializer() failed")
    val fixture = Fixture.create(lookUpSerializer = { throw InvocationTargetException(cause) })
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isNull()
    verifyLookUpFailureLogged(fixture.logger, cause)
  }

  @Test
  fun `returns null and logs the cause when the serializer lookup throws a wrapped fatal error`() {
    val cause = OutOfMemoryError("serializer() failed fatally")
    val fixture = Fixture.create(lookUpSerializer = { throw InvocationTargetException(cause) })
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isNull()
    verifyLookUpFailureLogged(fixture.logger, cause)
  }

  @Test
  fun `returns null and logs the InvocationTargetException when the serializer lookup throws one without a cause`() {
    val error = InvocationTargetException(null)
    val fixture = Fixture.create(lookUpSerializer = { throw error })
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isNull()
    verifyLookUpFailureLogged(fixture.logger, error)
  }

  @Test
  fun `returns null and logs the error when the serializer's descriptor throws`() {
    val error = IllegalStateException("descriptor failed")
    val serializer =
      object : NamingOnlySerializer<Detail>() {
        override val descriptor: SerialDescriptor
          get() = throw error
      }
    val fixture = Fixture.create(lookUpSerializer = { serializer })
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isNull()
    verifyLookUpFailureLogged(fixture.logger, error)
  }

  @Test
  fun `restores the interrupt flag when the serializer lookup throws InterruptedException`() {
    val fixture = Fixture.create(lookUpSerializer = { throw InterruptedException() })
    val mapper = fixture.mapper

    try {
      val mapped = mapper.map(Detail("1"))

      assertThat(mapped).isNull()
      assertThat(Thread.currentThread().isInterrupted).isTrue()
    } finally {
      Thread.interrupted()
    }
  }

  @Test
  fun `keeps naming other types after the serializer lookup throws for one type`() {
    val fixture =
      Fixture.create(
        lookUpSerializer = {
          if (it == Detail::class.java) throw NoSuchMethodError() else SERIALIZERS[it]
        },
      )
    val mapper = fixture.mapper

    val mapped = listOf(mapper.map(Detail("1")), mapper.map(Home), mapper.map(Search("shoes")))

    assertThat(mapped)
      .containsExactly(null, SentryBackStackEntry("Home"), SentryBackStackEntry("Search"))
      .inOrder()
  }

  @Test
  fun `propagates a fatal error the serializer lookup throws`() {
    val error = OutOfMemoryError()
    val fixture = Fixture.create(lookUpSerializer = { throw error })
    val mapper = fixture.mapper

    val thrown = assertFailsWith<OutOfMemoryError> { mapper.map(Detail("1")) }

    assertThat(thrown).isSameInstanceAs(error)
  }

  // Caching

  @Test
  fun `looks up each @Serializable type's serializer once, including failed lookups`() {
    val fixture =
      Fixture.create(lookUpSerializer = { if (it == Search::class.java) null else SERIALIZERS[it] })
    val mapper = fixture.mapper

    repeat(3) {
      mapper.map(Home)
      mapper.map(Detail("$it"))
      mapper.map(Search("$it"))
    }

    assertThat(fixture.lookedUpTypes)
      .containsExactly(Home::class.java, Detail::class.java, Search::class.java)
  }

  @Test
  fun `looks up an enum with constant bodies once for all its constants`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    mapper.map(TabWithBodies.FEED)
    mapper.map(TabWithBodies.SEARCH)

    assertThat(fixture.lookedUpTypes).containsExactly(TabWithBodies::class.java)
  }

  @Test
  fun `doesn't look up a type's serializer again after the serializer lookup throws`() {
    val fixture = Fixture.create(lookUpSerializer = { throw NoSuchMethodError() })
    val mapper = fixture.mapper

    repeat(3) { mapper.map(Detail("$it")) }

    assertThat(fixture.lookedUpTypes).containsExactly(Detail::class.java)
  }

  @Test
  fun `doesn't check a type's annotation or look up its serializer again after the annotation check throws`() {
    val checkedTypes = mutableListOf<Class<*>>()
    val fixture =
      Fixture.create(
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
  fun `looks up a type's serializer again after the serializer lookup throws a fatal error`() {
    var hasThrown = false
    val fixture =
      Fixture.create(
        lookUpSerializer = {
          if (!hasThrown) {
            hasThrown = true
            throw OutOfMemoryError()
          }
          Detail.serializer()
        },
      )
    val mapper = fixture.mapper
    assertFailsWith<OutOfMemoryError> { mapper.map(Detail("1")) }

    val mapped = mapper.map(Detail("2"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail"))
  }

  @Test
  fun `logs each type that isn't annotated @Serializable once`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    repeat(3) {
      mapper.map(NotSerializable("$it"))
      mapper.map("home")
      mapper.map(Home)
    }

    verifyNotSerializableLogged(fixture.logger, NotSerializable::class.java)
    verifyNotSerializableLogged(fixture.logger, String::class.java)
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `logs each generic @Serializable type once`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    repeat(3) {
      mapper.map(Generic("$it"))
      mapper.map(Home)
    }

    verify(fixture.logger)
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains("because the type has type parameters") },
        eq(Generic::class.java.name),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `logs each type whose serializer lookup fails once`() {
    val error = NoSuchMethodError()
    val fixture =
      Fixture.create(
        lookUpSerializer = {
          when (it) {
            Detail::class.java -> null
            Search::class.java -> throw error
            else -> SERIALIZERS[it]
          }
        },
      )
    val mapper = fixture.mapper

    repeat(3) {
      mapper.map(Detail("$it"))
      mapper.map(Search("$it"))
      mapper.map(Home)
    }

    verifySerializerNotFoundLogged(fixture.logger, Detail::class.java)
    verifyLookUpFailureLogged(fixture.logger, error, Search::class.java)
    verifyNoMoreInteractions(fixture.logger)
  }

  // Unknown entry reasons

  @Test
  fun `reports no reason for an entry it names`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    val (mapped, reason) = collectWithReason { mapper.map(Detail("1")) }

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail"))
    assertThat(reason).isNull()
  }

  @Test
  fun `reports entry not annotated for an entry whose type isn't annotated @Serializable`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    val (mapped, reason) = collectWithReason { mapper.map(NotSerializable("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.ENTRY_NOT_ANNOTATED)
  }

  @Test
  fun `reports serializer access threw for an entry whose annotation check throws an exception`() {
    val fixture =
      Fixture.create(
        isAnnotatedSerializable = { throw AnnotationFormatError("malformed annotation") },
      )
    val mapper = fixture.mapper

    val (mapped, reason) = collectWithReason { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_ACCESS_THREW)
  }

  @Test
  fun `reports serializer access threw for an entry whose annotation check throws a LinkageError`() {
    val fixture =
      Fixture.create(
        isAnnotatedSerializable = { throw NoClassDefFoundError("com/example/MissingAnnotation") },
      )
    val mapper = fixture.mapper

    val (mapped, reason) = collectWithReason { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_ACCESS_THREW)
  }

  @Test
  fun `reports generic entry type for an entry of a generic @Serializable type`() {
    val fixture = Fixture.create()
    val mapper = fixture.mapper

    val (mapped, reason) = collectWithReason { mapper.map(Generic("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.GENERIC_ENTRY_TYPE)
  }

  @Test
  fun `reports serializer not found for an entry whose serializer lookup finds none`() {
    val fixture = Fixture.create(lookUpSerializer = { null })
    val mapper = fixture.mapper

    val (mapped, reason) = collectWithReason { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_NOT_FOUND)
  }

  @Test
  fun `reports serializer access threw for an entry whose serializer lookup throws an exception`() {
    val fixture =
      Fixture.create(lookUpSerializer = { throw IllegalStateException("lookup failed") })
    val mapper = fixture.mapper

    val (mapped, reason) = collectWithReason { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_ACCESS_THREW)
  }

  @Test
  fun `reports serializer access threw for an entry whose serializer lookup throws a LinkageError`() {
    val fixture = Fixture.create(lookUpSerializer = { throw NoSuchMethodError() })
    val mapper = fixture.mapper

    val (mapped, reason) = collectWithReason { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_ACCESS_THREW)
  }

  @Test
  fun `reports the reason again for every later entry of a type that can't be named`() {
    val fixture =
      Fixture.create(lookUpSerializer = { if (it == Detail::class.java) null else SERIALIZERS[it] })
    val mapper = fixture.mapper
    val entries =
      listOf(
        Detail("1"),
        NotSerializable("1"),
        Generic("1"),
        Detail("2"),
        NotSerializable("2"),
        Generic("2"),
      )

    val reasons = entries.map { entry -> collectWithReason { mapper.map(entry) }.second }

    assertThat(reasons)
      .containsExactly(
        UnknownEntryReason.SERIALIZER_NOT_FOUND,
        UnknownEntryReason.ENTRY_NOT_ANNOTATED,
        UnknownEntryReason.GENERIC_ENTRY_TYPE,
        UnknownEntryReason.SERIALIZER_NOT_FOUND,
        UnknownEntryReason.ENTRY_NOT_ANNOTATED,
        UnknownEntryReason.GENERIC_ENTRY_TYPE,
      )
      .inOrder()
  }

  private fun verifyNotSerializableLogged(logger: ILogger, type: Class<*>) {
    verify(logger)
      .log(
        eq(SentryLevel.WARNING),
        argThat<String> { contains("because the type is not @Serializable") },
        eq(type.name),
      )
  }

  private fun verifySerializerNotFoundLogged(logger: ILogger, type: Class<*> = Detail::class.java) {
    verify(logger)
      .log(
        eq(SentryLevel.WARNING),
        isNull<Throwable>(),
        any<String>(),
        eq(type.name),
        eq(SERIALIZER_NOT_FOUND),
      )
  }

  private fun verifyLookUpFailureLogged(
    logger: ILogger,
    error: Throwable,
    type: Class<*> = Detail::class.java,
  ) {
    verify(logger)
      .log(eq(SentryLevel.WARNING), eq(error), any<String>(), eq(type.name), eq(LOOK_UP_FAILED))
  }

  private companion object {
    val SERIALIZERS: Map<Class<*>, KSerializer<*>> =
      mapOf(
        Home::class.java to Home.serializer(),
        Detail::class.java to Detail.serializer(),
        Search::class.java to Search.serializer(),
        TabWithBodies::class.java to TabWithBodies.serializer(),
      )
  }

  /**
   * A [KotlinxBackStackEntryMapper] whose serializer lookups are recorded in [lookedUpTypes].
   * Without an `isAnnotatedSerializable` check, the mapper reads each type's real `@Serializable`
   * annotation.
   */
  private class Fixture
  private constructor(
    lookUpSerializer: (Class<*>) -> KSerializer<*>?,
    isAnnotatedSerializable: ((Class<*>) -> Boolean)?,
    probe: CompatibilityProbe,
  ) {
    val logger = mock<ILogger>()
    val lookedUpTypes = mutableListOf<Class<*>>()
    val mapper: KotlinxBackStackEntryMapper

    init {
      val recordingLookUp: (Class<*>) -> KSerializer<*>? = { type ->
        lookedUpTypes += type
        lookUpSerializer(type)
      }
      mapper =
        if (isAnnotatedSerializable == null) {
          KotlinxBackStackEntryMapper(recordingLookUp, probe = probe, logger = { logger })
        } else {
          KotlinxBackStackEntryMapper(recordingLookUp, isAnnotatedSerializable, probe, { logger })
        }
    }

    companion object {
      fun create(
        lookUpSerializer: (Class<*>) -> KSerializer<*>? = { SERIALIZERS[it] },
        isAnnotatedSerializable: ((Class<*>) -> Boolean)? = null,
        probe: CompatibilityProbe = FakeCompatibilityProbe(),
      ) = Fixture(lookUpSerializer, isAnnotatedSerializable, probe)
    }
  }
}
