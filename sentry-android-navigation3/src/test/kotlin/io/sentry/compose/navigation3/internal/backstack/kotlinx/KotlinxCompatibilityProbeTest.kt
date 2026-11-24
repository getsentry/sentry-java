package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxCompatibilityProbe.Companion.ProbeClass
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxCompatibilityProbe.Companion.ProbeObject
import io.sentry.util.HttpUtils
import io.sentry.util.UrlUtils.SENSITIVE_DATA_SUBSTITUTE
import kotlin.test.assertFailsWith
import org.junit.Test

/**
 * Runs the default probe against the kotlinx.serialization version on the test classpath, which CI
 * also sets to the latest version with `-Pnav3TestKotlinxVersion`.
 */
class KotlinxCompatibilityProbeTest {

  // nameLookupLinkageError()

  @Test
  fun `nameLookupLinkageError() - returns null for a compatible kotlinx serialization version conclusively`() {
    val inconclusive = mutableListOf<Throwable?>()

    val error = KotlinxCompatibilityProbe.nameLookupLinkageError { inconclusive += it }

    assertThat(error).isNull()
    assertThat(inconclusive).isEmpty()
  }

  // encoderIncompatibility()

  @Test
  fun `encoderIncompatibility() - returns null for a compatible kotlinx serialization version`() {
    // A compatible version encodes the probe fixture to its expected output, so the probe's output
    // verification matches and it reports no incompatibility.
    val error = KotlinxCompatibilityProbe.encoderIncompatibility()

    assertThat(error).isNull()
  }

  // encoderFailureOf()

  @Test
  fun `encoderFailureOf() - returns null when the check reports no failure`() {
    val failure = KotlinxCompatibilityProbe.encoderFailureOf { null }

    assertThat(failure).isNull()
  }

  @Test
  fun `encoderFailureOf() - returns the failure the check reports without throwing`() {
    val error = EncoderProducedUnexpectedOutput()

    val failure = KotlinxCompatibilityProbe.encoderFailureOf { error }

    assertThat(failure).isSameInstanceAs(error)
  }

  @Test
  fun `encoderFailureOf() - returns a LinkageError the check throws instead of rethrowing it`() {
    // Rethrown, the error would escape the mapper's constructor and disable name recording too.
    val errors = listOf(NoSuchMethodError(), NoClassDefFoundError(), VerifyError())

    val failures = errors.map { error ->
      KotlinxCompatibilityProbe.encoderFailureOf { throw error }
    }

    assertThat(failures).containsExactlyElementsIn(errors).inOrder()
  }

  @Test
  fun `encoderFailureOf() - returns a non-fatal exception the check throws`() {
    val error = NullPointerException()

    val failure = KotlinxCompatibilityProbe.encoderFailureOf { throw error }

    assertThat(failure).isSameInstanceAs(error)
  }

  @Test
  fun `encoderFailureOf() - rethrows a fatal error the check throws that isn't a LinkageError`() {
    val error = OutOfMemoryError()

    val thrown =
      assertFailsWith<OutOfMemoryError> {
        KotlinxCompatibilityProbe.encoderFailureOf { throw error }
      }

    assertThat(thrown).isSameInstanceAs(error)
  }

  @Test
  fun `EncoderProducedUnexpectedOutput is a RuntimeException with a message that leaks no route data`() {
    val error = EncoderProducedUnexpectedOutput()

    assertThat(error).isInstanceOf(RuntimeException::class.java)
    assertThat(error)
      .hasMessageThat()
      .isEqualTo(
        "The kotlinx.serialization properties encoder produced unexpected output for a known " +
          "fixture.",
      )
  }

  // Probe types

  @Test
  fun `the serializer lookup names the probe's types`() {
    // A probe type the lookup couldn't find would make the check pass without looking up a name.
    val names =
      listOf(ProbeClass::class.java, ProbeObject::class.java).map {
        reflectiveSerializerOrNull(it)?.descriptor?.serialName
      }

    assertThat(names)
      .containsExactly(ProbeClass::class.qualifiedName, ProbeObject::class.qualifiedName)
      .inOrder()
  }

  @Test
  fun `the properties encoder encodes every property of the probe's class`() {
    // A probe type that failed to encode for another reason would make the check fail closed.
    // Spelled out independently of the probe's expected encoding, so a wrong expectation can't
    // pass by matching itself.
    val encoded = encodeToMap(ProbeClass.serializer(), ProbeClass())

    assertThat(encoded)
      .containsExactlyEntriesIn(
        mapOf(
          "name" to "probe",
          "count" to 1,
          "quantity" to 4_294_967_295L,
          "authToken" to SENSITIVE_DATA_SUBSTITUTE,
          "price" to 1,
          "login" to SENSITIVE_DATA_SUBSTITUTE,
          "sort" to "ascending",
          "values" to listOf(1),
          "sizes" to listOf(1, 2),
          "counts" to mapOf("count" to 1, "apiKey" to SENSITIVE_DATA_SUBSTITUTE),
          "filter" to mapOf("sessionId" to SENSITIVE_DATA_SUBSTITUTE, "query" to "query"),
          "filters" to listOf(mapOf("sessionId" to SENSITIVE_DATA_SUBSTITUTE, "query" to "query")),
          "origin" to mapOf("type" to "search", "value" to mapOf("term" to "query")),
          "fallbackOrigin" to mapOf("type" to "deeplink", "value" to emptyMap<String, Any?>()),
          "note" to null,
        ),
      )
  }

  @Test
  fun `the properties encoder omits the transient property of the probe's class`() {
    val encoded = encodeToMap(ProbeClass.serializer(), ProbeClass())

    assertThat(ProbeClass().cache).isNotNull()
    assertThat(encoded).doesNotContainKey("cache")
  }

  @Test
  fun `the probe's class filters a value class property only by its underlying property name`() {
    // The `login` property's own name isn't sensitive, so its filtered value proves the fixture
    // exercises the encoder's rule for a value class's underlying property name.
    val encoded = encodeToMap(ProbeClass.serializer(), ProbeClass())

    assertThat(HttpUtils.containsSensitiveDataKey("login")).isFalse()
    assertThat(HttpUtils.containsSensitiveDataKey("password")).isTrue()
    assertThat(encoded).containsEntry("login", SENSITIVE_DATA_SUBSTITUTE)
  }
}
