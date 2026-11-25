package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.kotlinx.FakeCompatibilityProbe.Inconclusive
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxBackStackEntryMapper.Companion.NAME_LOOKUP_CAPABILITY
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.PROBE_SERIALIZER_NOT_FOUND
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.PROBE_THREW
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions

/** How [KotlinxBackStackEntryMapper] acts on what its [KotlinxCompatibilityProbe] finds. */
class KotlinxBackStackEntryMapperProbeTest {

  @Before
  fun clearIntegrations() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  @After
  fun clearIntegrationsAfterTest() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  // Name lookup check

  @Test
  fun `logs nothing when created for a kotlinx serialization version the name lookup check finds compatible`() {
    val fixture = KotlinxMapperFixture.create(probe = FakeCompatibilityProbe())

    fixture.mapper

    verifyNoInteractions(fixture.logger)
  }

  @Test
  fun `throws the probe's LinkageError when created for a kotlinx serialization version that can't support name lookups`() {
    val error = NoSuchMethodError("serializerOrNull")
    val probe = FakeCompatibilityProbe(nameLookupError = error)

    val thrown =
      assertFailsWith<NoSuchMethodError> {
        KotlinxBackStackEntryMapper(includeProperties = false, logger = { mock() }, probe = probe)
      }

    assertThat(thrown).isSameInstanceAs(error)
  }

  @Test
  fun `doesn't log when created for a kotlinx serialization version that can't support name lookups`() {
    // BuiltInBackStackEntryMappers logs the error it catches, so the mapper doesn't log it too.
    val logger = mock<ILogger>()
    val probe = FakeCompatibilityProbe(nameLookupError = NoSuchMethodError())

    assertFailsWith<NoSuchMethodError> {
      KotlinxBackStackEntryMapper(includeProperties = true, logger = { logger }, probe = probe)
    }

    verifyNoInteractions(logger)
  }

  @Test
  fun `doesn't check the properties encoder when the name lookup check fails`() {
    val probe =
      FakeCompatibilityProbe(
        nameLookupError = NoSuchMethodError(),
        encoderError = NoSuchMethodError(),
      )

    assertFailsWith<NoSuchMethodError> {
      KotlinxBackStackEntryMapper(includeProperties = true, logger = { mock() }, probe = probe)
    }

    assertThat(probe.encodingChecks).isEqualTo(0)
  }

  @Test
  fun `names entries when the name lookup check is inconclusive`() {
    val probe =
      FakeCompatibilityProbe(nameLookupInconclusive = Inconclusive(IllegalStateException()))
    val fixture = KotlinxMapperFixture.create(probe = probe)
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail"))
  }

  @Test
  fun `logs at debug level with the error when the name lookup check throws inconclusively`() {
    val error = IllegalStateException("check failed")
    val probe = FakeCompatibilityProbe(nameLookupInconclusive = Inconclusive(error))

    val fixture = KotlinxMapperFixture.create(probe = probe)

    val message = argumentCaptor<String>()
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.DEBUG),
        eq(error),
        message.capture(),
        eq(NAME_LOOKUP_CAPABILITY),
        eq(PROBE_THREW),
      )
    assertThat(message.firstValue).startsWith(INCONCLUSIVE_PROBE)
  }

  @Test
  fun `logs at debug level when the name lookup check can't find a probe type's serializer`() {
    val probe = FakeCompatibilityProbe(nameLookupInconclusive = Inconclusive(error = null))

    val fixture = KotlinxMapperFixture.create(probe = probe)

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.DEBUG),
        isNull<Throwable>(),
        argThat<String> { startsWith(INCONCLUSIVE_PROBE) },
        eq(NAME_LOOKUP_CAPABILITY),
        eq(PROBE_SERIALIZER_NOT_FOUND),
      )
  }

  // Property encoding check

  @Test
  fun `logs nothing when created with properties included for a kotlinx serialization version the probe finds compatible`() {
    val fixture =
      KotlinxMapperFixture.create(includeProperties = true, probe = FakeCompatibilityProbe())

    fixture.mapper

    verifyNoInteractions(fixture.logger)
  }

  @Test
  fun `checks the properties encoder once when created with properties included`() {
    val probe = FakeCompatibilityProbe()
    val fixture = KotlinxMapperFixture.create(includeProperties = true, probe = probe)
    val mapper = fixture.mapper

    repeat(3) { mapper.mapAndReadArguments(Detail("$it")) }

    assertThat(probe.encodingChecks).isEqualTo(1)
  }

  @Test
  fun `doesn't check the properties encoder when properties are excluded`() {
    // No incompatible check is logged, as the check isn't run when properties are excluded.
    val probe = FakeCompatibilityProbe(encoderError = NoSuchMethodError())
    val fixture = KotlinxMapperFixture.create(includeProperties = false, probe = probe)
    val mapper = fixture.mapper

    val mapped = mapper.map(Detail("1"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail"))
    assertThat(probe.encodingChecks).isEqualTo(0)
    verifyNoInteractions(fixture.logger)
  }

  @Test
  fun `keeps the name and records only the kotlinx serialization incompatibility when the properties encoder is incompatible`() {
    val probe = FakeCompatibilityProbe(encoderError = NoSuchMethodError())
    val fixture = KotlinxMapperFixture.create(includeProperties = true, probe = probe)
    val mapper = fixture.mapper

    val mapped = listOf(mapper.map(Detail("1")), mapper.map(Search("shoes")))

    assertThat(mapped)
      .containsExactly(
        SentryBackStackEntry(
          "Detail",
          droppedBy(ArgumentDropReason.KOTLINX_SERIALIZATION_VERSION),
        ),
        SentryBackStackEntry(
          "Search",
          droppedBy(ArgumentDropReason.KOTLINX_SERIALIZATION_VERSION),
        ),
      )
      .inOrder()
  }

  @Test
  fun `records no arguments for objects when the properties encoder is incompatible`() {
    val probe = FakeCompatibilityProbe(encoderError = NoSuchMethodError())
    val fixture = KotlinxMapperFixture.create(includeProperties = true, probe = probe)
    val mapper = fixture.mapper

    val mapped = mapper.map(Home)

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Home", arguments = null))
  }

  @Test
  fun `doesn't encode properties when the properties encoder is incompatible`() {
    val probe = FakeCompatibilityProbe(encoderError = NoSuchMethodError())
    val fixture = KotlinxMapperFixture.create(includeProperties = true, probe = probe)
    val mapper = fixture.mapper

    mapper.mapAndReadArguments(Detail("1"))
    mapper.mapAndWriteArguments(Detail("2"))

    assertThat(fixture.detailSerializer.serialized).isEmpty()
  }

  @Test
  fun `logs a warning with the error and compatibility advice when created for an incompatible properties encoder`() {
    val error = NoSuchMethodError("encodeStructure")
    val probe = FakeCompatibilityProbe(encoderError = error)
    val fixture = KotlinxMapperFixture.create(includeProperties = true, probe = probe)

    val message = argumentCaptor<String>()
    verify(fixture.logger, times(1)).log(eq(SentryLevel.WARNING), message.capture(), eq(error))
    assertThat(message.firstValue).startsWith(INCOMPATIBLE_ENCODER)
    assertThat(message.firstValue).contains(INCOMPATIBLE_KOTLINX_ADVICE)
  }

  @Test
  fun `adds no Nav3 integration to the SDK when the properties encoder is incompatible`() {
    val probe = FakeCompatibilityProbe(encoderError = NoSuchMethodError())

    KotlinxMapperFixture.create(includeProperties = true, probe = probe)

    assertThat(
        SentryIntegrationPackageStorage.getInstance().integrations.filter {
          it.startsWith(NAV3_INTEGRATION_PREFIX)
        },
      )
      .isEmpty()
  }

  @Test
  fun `logs nothing more for entries mapped when the properties encoder is incompatible`() {
    val error = NoSuchMethodError()
    val probe = FakeCompatibilityProbe(encoderError = error)
    val fixture = KotlinxMapperFixture.create(includeProperties = true, probe = probe)
    val mapper = fixture.mapper

    repeat(3) {
      mapper.mapAndReadArguments(Detail("$it"))
      mapper.mapAndWriteArguments(Search("$it"))
    }

    verify(fixture.logger, times(1)).log(eq(SentryLevel.WARNING), any<String>(), eq(error))
    verifyNoMoreInteractions(fixture.logger)
  }

  @Test
  fun `attaches the probe's error to the incompatible properties encoder warning even if it isn't a LinkageError`() {
    val error = EncoderProducedUnexpectedOutput()
    val probe = FakeCompatibilityProbe(encoderError = error)
    val fixture = KotlinxMapperFixture.create(includeProperties = true, probe = probe)

    val message = argumentCaptor<String>()
    verify(fixture.logger, times(1)).log(eq(SentryLevel.WARNING), message.capture(), eq(error))
    assertThat(message.firstValue).startsWith(INCOMPATIBLE_ENCODER)
  }

  private companion object {
    const val NAV3_INTEGRATION_PREFIX = "ComposeNavigation3"

    const val INCONCLUSIVE_PROBE = "Nav3 couldn't verify kotlinx.serialization support for %s"

    const val INCOMPATIBLE_ENCODER =
      "Nav3 SerialNameAndProperties is incompatible with this app's kotlinx.serialization version."
  }
}
