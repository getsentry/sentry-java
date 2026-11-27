package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.LOOK_UP_FAILED
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.MIN_KOTLINX_SERIALIZATION_VERSION
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.PROBE_SERIALIZER_NOT_FOUND
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.PROBE_THREW
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.SERIALIZER_NOT_FOUND
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class KotlinxMapperDiagnosticsTest {

  private val logger = mock<ILogger>()
  private val diagnostics = KotlinxMapperDiagnostics { logger }

  private val type = Detail::class.java

  // notSerializable()

  @Test
  fun `notSerializable() - logs a warning for the type that advises adding @Serializable`() {
    diagnostics.notSerializable(type)

    val message = argumentCaptor<String>()
    verify(logger).log(eq(SentryLevel.WARNING), message.capture(), eq(type.name))
    assertThat(message.firstValue).contains("because the type is not @Serializable")
    assertThat(message.firstValue).contains("Add @Serializable")
  }

  // genericType()

  @Test
  fun `genericType() - logs a warning for the type that advises a custom BackStackEntryMapper`() {
    diagnostics.genericType(type)

    val message = argumentCaptor<String>()
    verify(logger).log(eq(SentryLevel.WARNING), message.capture(), eq(type.name))
    assertThat(message.firstValue).contains("because the type has type parameters")
    assertThat(message.firstValue).contains("Use a custom BackStackEntryMapper")
  }

  // inconclusiveProbe()

  @Test
  fun `inconclusiveProbe() - logs the error at debug level with the reason that the probe threw`() {
    val error = IllegalStateException("probe failed")

    diagnostics.inconclusiveProbe(error)

    val message = argumentCaptor<String>()
    verify(logger).log(eq(SentryLevel.DEBUG), eq(error), message.capture(), eq(PROBE_THREW))
    assertThat(message.firstValue)
      .startsWith("Nav3 couldn't verify kotlinx.serialization support for name lookups")
  }

  @Test
  fun `inconclusiveProbe() - logs at debug level that no probe serializer was found when there's no error`() {
    diagnostics.inconclusiveProbe(null)

    val message = argumentCaptor<String>()
    verify(logger)
      .log(
        eq(SentryLevel.DEBUG),
        isNull<Throwable>(),
        message.capture(),
        eq(PROBE_SERIALIZER_NOT_FOUND),
      )
    assertThat(message.firstValue)
      .startsWith("Nav3 couldn't verify kotlinx.serialization support for name lookups")
  }

  // unresolved()

  @Test
  fun `unresolved() - logs the error with advice to see it, without compatibility advice, for an exception`() {
    val error = IllegalStateException("lookup failed")

    diagnostics.unresolved(type, error)

    val message = loggedUnresolvedMessage(error, LOOK_UP_FAILED)
    assertThat(message).contains("See the attached error")
    assertThat(message).doesNotContain(INCOMPATIBLE_KOTLINX_ADVICE)
  }

  @Test
  fun `unresolved() - logs the error with compatibility advice for a LinkageError`() {
    val error = NoSuchMethodError("kotlinx.serialization.KSerializer.getDescriptor()")

    diagnostics.unresolved(type, error)

    val message = loggedUnresolvedMessage(error, LOOK_UP_FAILED)
    assertThat(message).contains("See the attached error")
    assertThat(message).contains(INCOMPATIBLE_KOTLINX_ADVICE)
  }

  @Test
  fun `unresolved() - logs the error without compatibility advice for an ExceptionInInitializerError`() {
    val error = ExceptionInInitializerError(IllegalStateException("serializer init failed"))

    diagnostics.unresolved(type, error)

    val message = loggedUnresolvedMessage(error, LOOK_UP_FAILED)
    assertThat(message).contains("See the attached error")
    assertThat(message).doesNotContain(INCOMPATIBLE_KOTLINX_ADVICE)
  }

  @Test
  fun `unresolved() - logs that no serializer was found with advice to see the docs when there's no error`() {
    diagnostics.unresolved(type, null)

    val message = loggedUnresolvedMessage(null, SERIALIZER_NOT_FOUND)
    assertThat(message).contains("See BackStackEntryMapper.SerialName's documentation")
    assertThat(message).doesNotContain(INCOMPATIBLE_KOTLINX_ADVICE)
  }

  // MIN_KOTLINX_SERIALIZATION_VERSION

  @Test
  fun `MIN_KOTLINX_SERIALIZATION_VERSION matches kotlinxSerialization-floor in the dependency matrix catalog`() {
    val floor =
      checkNotNull(System.getProperty(KOTLINX_SERIALIZATION_FLOOR_PROPERTY)) {
        "System property $KOTLINX_SERIALIZATION_FLOOR_PROPERTY is missing. Run this test through " +
          "Gradle, which sets it from kotlinxSerialization-floor in " +
          "gradle/dependency-matrix.versions.toml."
      }

    assertThat(MIN_KOTLINX_SERIALIZATION_VERSION).isEqualTo(floor)
  }

  /**
   * Returns the message of the warning [diagnostics] logged for [type] with [error] and [reason].
   */
  private fun loggedUnresolvedMessage(error: Throwable?, reason: String): String {
    val message = argumentCaptor<String>()
    verify(logger)
      .log(eq(SentryLevel.WARNING), eq(error), message.capture(), eq(type.name), eq(reason))
    return message.firstValue
  }

  private companion object {
    const val KOTLINX_SERIALIZATION_FLOOR_PROPERTY = "nav3.kotlinxSerializationFloor"
  }
}
