@file:Suppress("MatchingDeclarationName")

package io.sentry.compose.navigation3.internal.backstack.kotlinx

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable @SerialName("Home") internal data object Home

@Serializable @SerialName("Detail") internal data class Detail(val id: String)

@Serializable @SerialName("Search") internal data class Search(val query: String)

/** A serializer for tests that only read its descriptor, e.g., to name an entry. */
internal abstract class NamingOnlySerializer<T> : KSerializer<T> {
  override fun serialize(encoder: Encoder, value: T) = throw UnsupportedOperationException()

  override fun deserialize(decoder: Decoder): T = throw UnsupportedOperationException()
}

/**
 * A compatibility probe that finds kotlinx.serialization compatible by default. Set [linkageError]
 * to report an incompatible version, or [inconclusive] to report an inconclusive result.
 */
internal class FakeCompatibilityProbe(
  private val linkageError: LinkageError? = null,
  private val inconclusive: Inconclusive? = null,
) : CompatibilityProbe {

  override fun invoke(onInconclusive: (Throwable?) -> Unit): LinkageError? {
    linkageError?.let {
      return it
    }
    inconclusive?.let { onInconclusive(it.error) }
    return null
  }

  /** Why the probe is inconclusive: [error], or `null` if a probe serializer wasn't found. */
  class Inconclusive(val error: Throwable?)
}
