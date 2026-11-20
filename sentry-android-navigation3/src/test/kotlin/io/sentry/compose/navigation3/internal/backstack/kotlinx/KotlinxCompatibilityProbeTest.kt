package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxCompatibilityProbe.Companion.ProbeClass
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxCompatibilityProbe.Companion.ProbeObject
import kotlin.test.assertFailsWith
import org.junit.Test

/**
 * Runs the default probe against the kotlinx.serialization version on the test classpath, which CI
 * also sets to the latest stable release and any newer release candidate with
 * `-Pnav3TestKotlinxVersion`.
 */
class KotlinxCompatibilityProbeTest {

  // nameLookupLinkageError()

  @Test
  fun `nameLookupLinkageError() - returns null without an inconclusive result for a compatible kotlinx serialization version`() {
    val inconclusive = mutableListOf<Throwable?>()

    val error = KotlinxCompatibilityProbe.nameLookupLinkageError { inconclusive += it }

    assertThat(error).isNull()
    assertThat(inconclusive).isEmpty()
  }

  @Test
  fun `nameLookupLinkageError() - returns the LinkageError a probe type's serializer lookup throws`() {
    val error = NoSuchMethodError("serializerOrNull")
    val inconclusive = mutableListOf<Throwable?>()

    val returned =
      KotlinxCompatibilityProbe.nameLookupLinkageError({ throw error }) { inconclusive += it }

    assertThat(returned).isSameInstanceAs(error)
    assertThat(inconclusive).isEmpty()
  }

  @Test
  fun `nameLookupLinkageError() - reports a non-fatal exception as inconclusive and returns null`() {
    val error = IllegalStateException("lookup failed")
    val inconclusive = mutableListOf<Throwable?>()

    val returned =
      KotlinxCompatibilityProbe.nameLookupLinkageError({ throw error }) { inconclusive += it }

    assertThat(returned).isNull()
    assertThat(inconclusive).containsExactly(error)
  }

  @Test
  fun `nameLookupLinkageError() - propagates a fatal error that isn't a LinkageError`() {
    val error = OutOfMemoryError()

    val thrown =
      assertFailsWith<OutOfMemoryError> {
        KotlinxCompatibilityProbe.nameLookupLinkageError({ throw error }) {}
      }

    assertThat(thrown).isSameInstanceAs(error)
  }

  @Test
  fun `nameLookupLinkageError() - reports a missing probe serializer as inconclusive without an error`() {
    val inconclusive = mutableListOf<Throwable?>()

    val returned =
      KotlinxCompatibilityProbe.nameLookupLinkageError(
        { type -> if (type == ProbeObject::class.java) null else reflectiveSerializerOrNull(type) },
      ) {
        inconclusive += it
      }

    assertThat(returned).isNull()
    assertThat(inconclusive).containsExactly(null)
  }

  @Test
  fun `nameLookupLinkageError() - doesn't report an exception onInconclusive throws for a missing probe serializer back to onInconclusive`() {
    val error = IllegalStateException("callback failed")
    val inconclusive = mutableListOf<Throwable?>()

    val thrown =
      assertFailsWith<IllegalStateException> {
        KotlinxCompatibilityProbe.nameLookupLinkageError({ null }) {
          inconclusive += it
          throw error
        }
      }

    assertThat(thrown).isSameInstanceAs(error)
    assertThat(inconclusive).containsExactly(null)
  }

  @Test
  fun `nameLookupLinkageError() - doesn't report an exception onInconclusive throws for a lookup exception back to onInconclusive`() {
    val lookupError = IllegalStateException("lookup failed")
    val callbackError = IllegalArgumentException("callback failed")
    val inconclusive = mutableListOf<Throwable?>()

    val thrown =
      assertFailsWith<IllegalArgumentException> {
        KotlinxCompatibilityProbe.nameLookupLinkageError({ throw lookupError }) {
          inconclusive += it
          throw callbackError
        }
      }

    assertThat(thrown).isSameInstanceAs(callbackError)
    assertThat(inconclusive).containsExactly(lookupError)
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
}
