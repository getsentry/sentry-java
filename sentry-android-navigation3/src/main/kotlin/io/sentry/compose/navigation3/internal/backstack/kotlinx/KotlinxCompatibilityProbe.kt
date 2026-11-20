@file:OptIn(ExperimentalSerializationApi::class)

package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import io.sentry.util.ExceptionUtils
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Checks whether the app's kotlinx.serialization version successfully links against the APIs used
 * by [KotlinxBackStackEntryMapper]. It does so via two built-in probe types ([ProbeClass] and
 * [ProbeObject]).
 *
 * **Disambiguating serializer errors from library incompatibility**
 *
 * A [LinkageError] while handling a host app's back stack entry may come from that entry's
 * serializer, so it mustn't disable mapping for other types. The SDK-owned `ProbeClass` and
 * `ProbeObject` run no host app code, letting us check kotlinx.serialization compatibility
 * independently.
 *
 * The checks use different failure policies:
 *
 * - [nameLookupLinkageError]: only a [LinkageError] establishes incompatibility. Other failures,
 *   including a missing probe serializer, are reported through `onInconclusive`. Mapping continues,
 *   and failures for real entry types are handled and logged individually.
 *
 * - [encoderIncompatibility]: any exception or incorrect output disables property encoding. A
 *   broken encoder can silently record wrong arguments, so recording none is safer.
 */
internal interface KotlinxCompatibilityProbe {

  /**
   * Verifies [KotlinxBackStackEntryMapper]'s serializer and name lookups with [ProbeClass] and
   * [ProbeObject].
   *
   * Returns a [LinkageError] if those lookups can't link against the host app's
   * kotlinx.serialization version, or `null` if they succeed or the check is inconclusive.
   *
   * Calls [onInconclusive] for each inconclusive result, passing the cause or `null` if a probe
   * type's serializer is unavailable.
   */
  fun nameLookupLinkageError(onInconclusive: (Throwable?) -> Unit): LinkageError?

  /**
   * Encodes [ProbeClass] using the same path as [KotlinxBackStackEntryMapper], checks that it links
   * and runs, and verifies its output matches the known fixture.
   *
   * Returns an error if encoding fails or produces unexpected arguments, and `null` otherwise.
   * Unlike [nameLookupLinkageError], every failure disables property encoding. An incompatible
   * kotlinx.serialization version may throw something other than [LinkageError] (e.g.,
   * [NullPointerException] under R8), or silently encode incorrect arguments.
   */
  fun encoderIncompatibility(): Throwable?

  /** Probes the app's kotlinx.serialization version using [ProbeClass] and [ProbeObject]. */
  companion object : KotlinxCompatibilityProbe {

    override fun nameLookupLinkageError(onInconclusive: (Throwable?) -> Unit): LinkageError? {
      var isSerializerMissing = false

      val error =
        probe(onInconclusive) {
          val descriptors =
            listOf(ProbeClass::class.java, ProbeObject::class.java).map { type ->
              reflectiveSerializerOrNull(type)?.descriptor
            }
          for (descriptor in descriptors.filterNotNull()) {
            descriptor.serialName
            descriptor.elementsCount
            descriptor.kind
          }
          isSerializerMissing = null in descriptors
        }

      // Call outside probe() so an exception from onInconclusive isn't caught and reported back to
      // the same callback. If a serializer is missing, the remaining lookup steps cannot be checked
      // for that type.
      if (isSerializerMissing) {
        onInconclusive(null)
      }
      return error
    }

    override fun encoderIncompatibility(): Throwable? =
      try {
        val writer = TreeArgumentWriter(ArgumentBudget())
        @Suppress("UNCHECKED_CAST")
        KotlinxPropertiesEncoder.encode(
          ProbeClass.serializer() as KSerializer<Any>,
          ProbeClass(),
          writer,
        )
        // An incompatible kotlinx.serialization version can link and run yet mis-encode properties
        // under R8 without throwing. Verifying the fixture's output disables such a silently
        // corrupting encoder instead of letting wrong arguments reach Sentry.
        if (writer.argumentsOrThrow() == EXPECTED_PROBE_ENCODING) {
          null
        } else {
          EncoderProducedUnexpectedOutput()
        }
      } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        // Broad because an incompatible kotlinx.serialization version can fail the encoder in many
        // ways, not only with a LinkageError. Any failure disables property encoding but keeps
        // names, as recording wrong arguments would be worse than recording none.
        ExceptionUtils.rethrowIfFatal(t)
        t
      }

    /**
     * Runs [check], returning any [LinkageError] and rethrowing fatal errors. Returns `null` if the
     * check succeeds or is inconclusive.
     *
     * Reports other errors to [onInconclusive], passing the cause.
     */
    private inline fun probe(
      onInconclusive: (Throwable) -> Unit,
      check: () -> Unit,
    ): LinkageError? =
      try {
        check()
        null
      } catch (e: LinkageError) {
        e
      } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        // Broad because the app's kotlinx.serialization version may throw anything. Only
        // LinkageError confirms incompatibility. Other non-fatal failures are inconclusive.
        // Failures for actual back stack types are handled and logged per type.
        ExceptionUtils.rethrowIfFatal(t)
        onInconclusive(t)
        null
      }

    /**
     * A known serializable type used to test whether the app's kotlinx.serialization version
     * supports serializer lookup and property encoding. Its scalar, list, and map properties
     * exercise common encoding paths without running host app code.
     *
     * Internal visibility lets the probe look up its serializer the same way
     * [KotlinxBackStackEntryMapper] looks up serializers for the host app's public back stack entry
     * types.
     */
    @Serializable
    internal data class ProbeClass(
      val name: String = "probe",
      val values: List<Int> = listOf(1),
      val counts: Map<String, Int> = mapOf("count" to 1),
    )

    /**
     * [ProbeClass]'s expected encoding.
     *
     * A kotlinx.serialization version that encodes it to anything else mis-encodes properties and
     * must not be used. None of the keys are on the sensitive denylist, so none are filtered.
     */
    private val EXPECTED_PROBE_ENCODING: Map<String, Any?> =
      mapOf("name" to "probe", "values" to listOf(1), "counts" to mapOf("count" to 1))

    /**
     * A known serializable object used to test serializer lookup for the host app's object-based
     * back stack entries. This lookup reads the object's singleton instance.
     */
    @Serializable internal object ProbeObject
  }
}

/**
 * Signals that the encoder produced unexpected arguments for the SDK-owned `ProbeClass`, indicating
 * incompatible kotlinx.serialization behavior.
 *
 * Carries a fixed message with no (potentially sensitive) back stack entry data.
 */
internal class EncoderProducedUnexpectedOutput :
  RuntimeException(
    "The kotlinx.serialization properties encoder produced unexpected output for a known fixture.",
  )
