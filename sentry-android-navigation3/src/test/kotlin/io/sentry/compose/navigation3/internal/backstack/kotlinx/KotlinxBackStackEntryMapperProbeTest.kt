package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.kotlinx.FakeCompatibilityProbe.Inconclusive
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxBackStackEntryMapper.Companion.NAME_LOOKUP_CAPABILITY
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.PROBE_SERIALIZER_NOT_FOUND
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.PROBE_THREW
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

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
        KotlinxBackStackEntryMapper(logger = { mock() }, probe = probe)
      }

    assertThat(thrown).isSameInstanceAs(error)
  }

  @Test
  fun `doesn't log when created for a kotlinx serialization version that can't support name lookups`() {
    // BuiltInBackStackEntryMappers logs the error it catches, so the mapper doesn't log it too.
    val logger = mock<ILogger>()
    val probe = FakeCompatibilityProbe(nameLookupError = NoSuchMethodError())

    assertFailsWith<NoSuchMethodError> {
      KotlinxBackStackEntryMapper(logger = { logger }, probe = probe)
    }

    verifyNoInteractions(logger)
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

  private companion object {
    const val NAV3_INTEGRATION_PREFIX = "ComposeNavigation3"

    const val INCONCLUSIVE_PROBE = "Nav3 couldn't verify kotlinx.serialization support for %s"
  }
}
