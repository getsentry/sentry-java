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

  // encodingLinkageError()

  @Test
  fun `encodingLinkageError() - returns null for a compatible kotlinx serialization version`() {
    val error = KotlinxCompatibilityProbe.encodingLinkageError {}

    assertThat(error).isNull()
  }

  @Test
  fun `encodingLinkageError() - finds a compatible kotlinx serialization version conclusively`() {
    val inconclusive = mutableListOf<Throwable>()

    KotlinxCompatibilityProbe.encodingLinkageError { inconclusive += it }

    assertThat(inconclusive).isEmpty()
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
    // A probe type that failed to encode for another reason would make the check inconclusive.
    val encoded = encodeToMap(ProbeClass.serializer(), ProbeClass())

    assertThat(encoded)
      .containsExactly("name", "probe", "values", listOf(1), "counts", mapOf("count" to 1))
  }
}
