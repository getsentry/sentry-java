@file:OptIn(ExperimentalSerializationApi::class)

package io.sentry.compose.navigation3.internal.backstack.kotlinx

import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import io.sentry.util.ExceptionUtils
import io.sentry.util.UrlUtils
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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

    override fun nameLookupLinkageError(onInconclusive: (Throwable?) -> Unit): LinkageError? =
      nameLookupLinkageError(::reflectiveSerializerOrNull, onInconclusive)

    /**
     * Like [nameLookupLinkageError], but looks up the probe types' serializers with
     * [lookUpSerializer], so tests can make the lookup fail.
     */
    internal fun nameLookupLinkageError(
      lookUpSerializer: (Class<*>) -> KSerializer<*>?,
      onInconclusive: (Throwable?) -> Unit,
    ): LinkageError? {
      var isSerializerMissing = false

      val error =
        probe(onInconclusive) {
          val descriptors =
            listOf(ProbeClass::class.java, ProbeObject::class.java).map { type ->
              lookUpSerializer(type)?.descriptor
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

    override fun encoderIncompatibility(): Throwable? = encoderIncompatibility { writer ->
      @Suppress("UNCHECKED_CAST")
      KotlinxPropertiesEncoder.encode(
        ProbeClass.serializer() as KSerializer<Any>,
        ProbeClass(),
        writer,
      )
    }

    /**
     * Like [encoderIncompatibility], but encodes [ProbeClass] to the writer with [encode], so tests
     * can make the encoder fail or produce unexpected output.
     */
    internal fun encoderIncompatibility(encode: (TreeArgumentWriter) -> Unit): Throwable? =
      encoderFailureOf {
        val writer = TreeArgumentWriter(ArgumentBudget())
        encode(writer)
        // An incompatible kotlinx.serialization version can link and run yet mis-encode properties
        // under R8 without throwing. Verifying the fixture's output disables such a silently
        // corrupting encoder instead of letting wrong arguments reach Sentry.
        if (writer.argumentsOrThrow() == EXPECTED_PROBE_ENCODING) {
          null
        } else {
          EncoderProducedUnexpectedOutput()
        }
      }

    /**
     * Runs [check], returning the failure it reports or throws, or `null` if it reports none.
     * Rethrows fatal errors other than [LinkageError].
     *
     * A [LinkageError] is returned rather than rethrown so that an encoder that can't link only
     * disables property encoding. Rethrown, it would escape the mapper's constructor, and
     * `BuiltInBackStackEntryMappers` would fall back to a mapper that records no names either.
     */
    internal inline fun encoderFailureOf(check: () -> Throwable?): Throwable? =
      try {
        check()
      } catch (e: LinkageError) {
        // Caught before rethrowIfFatal, which rethrows LinkageErrors.
        e
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
     * supports serializer lookup and property encoding, without running host app code.
     *
     * Its properties use every encoding rule of [KotlinxPropertiesEncoder] that the encoder relies
     * on kotlinx.serialization's serializers for: primitives, unsigned numbers, value classes,
     * enums with serial names, lists, sets, maps, nested classes, sealed polymorphism, nullable
     * values, and transient properties. It also has sensitive property names, a sensitive map key,
     * and a value class with a sensitive underlying property name, which must be filtered. A
     * kotlinx.serialization version that changes how its serializers drive the encoder for any of
     * these is likely to change the encoding, so the probe catches it on versions CI never tested,
     * before wrong arguments reach Sentry.
     *
     * Internal visibility lets the probe look up its serializer the same way
     * [KotlinxBackStackEntryMapper] looks up serializers for the host app's public back stack entry
     * types.
     */
    @Serializable
    internal data class ProbeClass(
      val name: String = "probe",
      val count: Int = 1,
      val quantity: UInt = UInt.MAX_VALUE,
      val authToken: String = "secret",
      val price: ProbePrice = ProbePrice(1),
      val login: ProbeLogin = ProbeLogin("secret"),
      val sort: ProbeSort = ProbeSort.ASCENDING,
      val values: List<Int> = listOf(1),
      val sizes: Set<Int> = setOf(1, 2),
      val counts: Map<String, Int> = mapOf("count" to 1, "apiKey" to 2),
      val filter: ProbeFilter = ProbeFilter(),
      val filters: List<ProbeFilter> = listOf(ProbeFilter()),
      val origin: ProbeOrigin = ProbeOrigin.Search("query"),
      val fallbackOrigin: ProbeOrigin = ProbeOrigin.DeepLink,
      val note: String? = null,
      @Transient val cache: String = "not encoded",
    )

    /** A value class, which [KotlinxPropertiesEncoder] records as its underlying value. */
    @Serializable @JvmInline internal value class ProbePrice(val cents: Int)

    /** A value class whose underlying property's name is sensitive, so its value is filtered. */
    @Serializable @JvmInline internal value class ProbeLogin(val password: String)

    @Serializable
    internal enum class ProbeSort {
      @SerialName("ascending") ASCENDING,
    }

    @Serializable
    internal data class ProbeFilter(val sessionId: String = "secret", val query: String = "query")

    @Serializable
    internal sealed interface ProbeOrigin {
      @Serializable @SerialName("search") data class Search(val term: String) : ProbeOrigin

      @Serializable @SerialName("deeplink") data object DeepLink : ProbeOrigin
    }

    /**
     * [ProbeClass]'s expected encoding.
     *
     * A kotlinx.serialization version that encodes it to anything else mis-encodes properties and
     * must not be used.
     */
    private val EXPECTED_PROBE_ENCODING: Map<String, Any?> =
      mapOf(
        "name" to "probe",
        "count" to 1,
        "quantity" to 4_294_967_295L,
        "authToken" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE,
        "price" to 1,
        "login" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE,
        "sort" to "ascending",
        "values" to listOf(1),
        "sizes" to listOf(1, 2),
        "counts" to mapOf("count" to 1, "apiKey" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE),
        "filter" to mapOf("sessionId" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE, "query" to "query"),
        "filters" to
          listOf(mapOf("sessionId" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE, "query" to "query")),
        "origin" to mapOf("type" to "search", "value" to mapOf("term" to "query")),
        "fallbackOrigin" to mapOf("type" to "deeplink", "value" to emptyMap<String, Any?>()),
        "note" to null,
      )

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
