package io.sentry.uitest.android.mockservers

import io.sentry.EnvelopeReader
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.uitest.android.describeForTest
import java.io.IOException
import java.util.zip.GZIPInputStream
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.GzipSource
import okio.buffer

/** Class used to assert requests sent to [MockRelay]. */
class RelayAsserter(private val unassertedEnvelopes: MutableList<RelayResponse>) {
  private val originalUnassertedEnvelopes: MutableList<RelayResponse> =
    ArrayList(unassertedEnvelopes)

  /**
   * Parses the first request as an envelope and makes other assertions through a
   * [EnvelopeAsserter]. The asserted envelope is then removed from internal list of unasserted
   * envelopes.
   */
  fun assertFirstEnvelope(assertion: (asserter: EnvelopeAsserter) -> Unit) {
    val relayResponse =
      unassertedEnvelopes.removeFirstOrNull() ?: throw AssertionError("No envelope request found")
    relayResponse.assert(assertion)
  }

  /**
   * Returns the first request that can be parsed as an envelope and that satisfies [filter]. Throws
   * an [AssertionError] if the envelope was not found.
   */
  fun findEnvelope(filter: (envelope: SentryEnvelope) -> Boolean = { true }): RelayResponse {
    val relayResponseIndex = unassertedEnvelopes.indexOfFirst { it.envelope?.let(filter) ?: false }
    if (relayResponseIndex == -1) {
      throw AssertionError(
        "No envelope request found with specified filter.\n" +
          "There was a total of ${originalUnassertedEnvelopes.size} envelopes: " +
          originalUnassertedEnvelopes.joinToString { it.envelope!!.describeForTest() }
      )
    }
    return unassertedEnvelopes.removeAt(relayResponseIndex)
  }

  /** Asserts no other envelopes were sent. */
  fun assertNoOtherEnvelopes() {
    if (unassertedEnvelopes.isNotEmpty()) {
      throw AssertionError(
        "There was a total of ${originalUnassertedEnvelopes.size} envelopes: " +
          originalUnassertedEnvelopes.joinToString { it.envelope!!.describeForTest() }
      )
    }
  }

  data class RelayResponse(val request: RecordedRequest, val response: MockResponse) {

    /** Request parsed as envelope. */
    val envelope: SentryEnvelope? by lazy {
      try {
        EnvelopeReader(Sentry.getCurrentScopes().options.serializer)
          .read(GZIPInputStream(request.body.inputStream()))
      } catch (e: IOException) {
        null
      }
    }

    /** Run [assertion] on this request parsed as an envelope. */
    fun assert(assertion: (asserter: EnvelopeAsserter) -> Unit) {
      // Parse the request to rebuild the original envelope. If it fails we throw an assertion
      // error.
      envelope?.let { assertion(EnvelopeAsserter(it, response)) }
        ?: throw AssertionError("Was unable to parse the request as an envelope: $request")
    }

    override fun toString(): String {
      return "RelayResponse(request=${request.requestLine}\n${GzipSource(request.body).buffer().readUtf8()}\n, response=$response)"
    }
  }
}
