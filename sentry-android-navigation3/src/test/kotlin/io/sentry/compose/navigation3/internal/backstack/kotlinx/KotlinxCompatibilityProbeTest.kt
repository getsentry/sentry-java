package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxCompatibilityProbe.Companion.ProbeClass
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxCompatibilityProbe.Companion.ProbeObject
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
    val encoded = encodeToMap(ProbeClass.serializer(), ProbeClass())

    assertThat(encoded)
      .containsExactly("name", "probe", "values", listOf(1), "counts", mapOf("count" to 1))
  }
}
