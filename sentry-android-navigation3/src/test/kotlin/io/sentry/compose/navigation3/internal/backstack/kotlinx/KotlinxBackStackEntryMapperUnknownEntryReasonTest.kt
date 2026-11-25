package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.UnknownEntryReason
import io.sentry.compose.navigation3.internal.backstack.UnknownEntryReasonReports
import java.lang.annotation.AnnotationFormatError
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import org.junit.Test

class KotlinxBackStackEntryMapperUnknownEntryReasonTest {

  data class NotSerializable(val id: String)

  @Serializable data class Generic<T>(val value: T)

  private val throwingDescriptorSerializer =
    object : NamingOnlySerializer<Any>() {
      override val descriptor: SerialDescriptor
        get() = error("descriptor failed")
    }

  @Test
  fun `reports no reason for an entry it names`() {
    val fixture = KotlinxMapperFixture.create()
    val mapper = fixture.mapper

    val (mapped, reason) = UnknownEntryReasonReports.collect { mapper.map(Detail("1")) }

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail"))
    assertThat(reason).isNull()
  }

  @Test
  fun `reports not serializable for an entry whose type isn't @Serializable`() {
    val fixture = KotlinxMapperFixture.create()
    val mapper = fixture.mapper

    val (mapped, reason) = UnknownEntryReasonReports.collect { mapper.map(NotSerializable("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.NOT_SERIALIZABLE)
  }

  @Test
  fun `reports generic type for an entry whose @Serializable type has type parameters`() {
    val fixture = KotlinxMapperFixture.create()
    val mapper = fixture.mapper

    val (mapped, reason) = UnknownEntryReasonReports.collect { mapper.map(Generic("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.GENERIC_TYPE)
  }

  @Test
  fun `reports serializer not found for an entry whose serializer lookup finds none`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { null }
    val mapper = fixture.mapper

    val (mapped, reason) = UnknownEntryReasonReports.collect { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_NOT_FOUND)
  }

  @Test
  fun `reports serializer lookup threw for an entry whose serializer lookup throws`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw IllegalStateException("lookup failed") }
    val mapper = fixture.mapper

    val (mapped, reason) = UnknownEntryReasonReports.collect { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_LOOKUP_THREW)
  }

  @Test
  fun `reports serializer lookup threw for an entry whose serializer lookup throws a LinkageError`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw NoSuchMethodError() }
    val mapper = fixture.mapper

    val (mapped, reason) = UnknownEntryReasonReports.collect { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_LOOKUP_THREW)
  }

  @Test
  fun `reports serializer lookup threw for an entry whose serializer's descriptor throws`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throwingDescriptorSerializer }
    val mapper = fixture.mapper

    val (mapped, reason) = UnknownEntryReasonReports.collect { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_LOOKUP_THREW)
  }

  @Test
  fun `reports serializer lookup threw for an entry whose annotation check throws an exception`() {
    val fixture =
      KotlinxMapperFixture.create(
        isAnnotatedSerializable = { throw AnnotationFormatError("malformed annotation") },
      )
    val mapper = fixture.mapper

    val (mapped, reason) = UnknownEntryReasonReports.collect { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_LOOKUP_THREW)
  }

  @Test
  fun `reports serializer lookup threw for an entry whose annotation check throws a LinkageError`() {
    val fixture =
      KotlinxMapperFixture.create(
        isAnnotatedSerializable = { throw NoClassDefFoundError("com/example/MissingAnnotation") },
      )
    val mapper = fixture.mapper

    val (mapped, reason) = UnknownEntryReasonReports.collect { mapper.map(Detail("1")) }

    assertThat(mapped).isNull()
    assertThat(reason).isEqualTo(UnknownEntryReason.SERIALIZER_LOOKUP_THREW)
  }

  // Later entries of a type that can't be named

  @Test
  fun `reports the reason again for every later entry of a type that can't be named`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { if (it == Detail::class.java) null else fixture.serializers[it] }
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

    val reasons = entries.map { entry ->
      UnknownEntryReasonReports.collect { mapper.map(entry) }.second
    }

    assertThat(reasons)
      .containsExactly(
        UnknownEntryReason.SERIALIZER_NOT_FOUND,
        UnknownEntryReason.NOT_SERIALIZABLE,
        UnknownEntryReason.GENERIC_TYPE,
        UnknownEntryReason.SERIALIZER_NOT_FOUND,
        UnknownEntryReason.NOT_SERIALIZABLE,
        UnknownEntryReason.GENERIC_TYPE,
      )
      .inOrder()
  }

  @Test
  fun `reports the reason again for every later entry of a type whose serializer lookup threw`() {
    val fixture = KotlinxMapperFixture.create()
    fixture.lookUp = { throw IllegalStateException("lookup failed") }
    val mapper = fixture.mapper

    val reasons = List(3) { UnknownEntryReasonReports.collect { mapper.map(Detail("$it")) }.second }

    assertThat(reasons)
      .containsExactly(
        UnknownEntryReason.SERIALIZER_LOOKUP_THREW,
        UnknownEntryReason.SERIALIZER_LOOKUP_THREW,
        UnknownEntryReason.SERIALIZER_LOOKUP_THREW,
      )
  }
}
