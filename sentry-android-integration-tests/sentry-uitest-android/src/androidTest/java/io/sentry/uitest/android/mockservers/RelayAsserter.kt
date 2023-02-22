package io.sentry.uitest.android.mockservers

import io.sentry.EnvelopeReader
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.io.IOException
import java.util.zip.GZIPInputStream

/** Class used to assert requests sent to [MockRelay]. */
class RelayAsserter(
    private val unassertedEnvelopes: MutableList<RelayResponse>,
    private val unassertedRequests: MutableList<RelayResponse>
) {

    /**
     * Asserts an envelope request exists and allows to make other assertions on the first one and on its response.
     * The asserted envelope request is then removed from internal list of unasserted envelopes.
     */
    fun assertFirstRawEnvelope(assertion: ((relayResponse: RelayResponse) -> Unit)? = null) {
        val relayResponse = unassertedEnvelopes.removeFirstOrNull()
            ?: throw AssertionError("No envelope request found")
        assertion?.let { it(relayResponse) }
    }

    /**
     * Asserts a request exists and makes other assertions on the first one and on its response.
     * The asserted request is then removed from internal list of unasserted requests.
     */
    fun assertRawRequest(assertion: ((request: RecordedRequest, response: MockResponse) -> Unit)? = null) {
        val relayResponse = unassertedRequests.removeFirstOrNull()
            ?: throw AssertionError("No raw request found")
        assertion?.let {
            it(relayResponse.request, relayResponse.response)
        }
    }

    /**
     * Parses the first request as an envelope and makes other assertions through a [EnvelopeAsserter].
     * The asserted envelope is then removed from internal list of unasserted envelopes.
     */
    fun assertFirstEnvelope(assertion: (asserter: EnvelopeAsserter) -> Unit) {
        assertFirstRawEnvelope { relayResponse ->
            relayResponse.assert(assertion)
        }
    }

    /**
     * Returns the first request that can be parsed as an envelope and that satisfies [filter].
     * Throws an [AssertionError] if the envelope was not found.
     */
    fun findEnvelope(
        filter: (envelope: SentryEnvelope) -> Boolean = { true }
    ): RelayResponse {
        val relayResponseIndex = unassertedEnvelopes.indexOfFirst { it.envelope?.let(filter) ?: false }
        if (relayResponseIndex == -1) throw AssertionError("No envelope request found with specified filter")
        return unassertedEnvelopes.removeAt(relayResponseIndex)
    }

    /** Asserts no other envelopes were sent. */
    fun assertNoOtherEnvelopes() {
        if (unassertedEnvelopes.isNotEmpty()) {
            throw AssertionError("There were other ${unassertedEnvelopes.size} envelope requests: $unassertedEnvelopes")
        }
    }

    /** Asserts no other raw requests were sent. */
    fun assertNoOtherRawRequests() {
        assertNoOtherEnvelopes()
        if (unassertedRequests.isNotEmpty()) {
            throw AssertionError("There were other ${unassertedRequests.size} requests: $unassertedRequests")
        }
    }

    /** Asserts no other requests or envelopes were sent. */
    fun assertNoOtherRequests() {
        assertNoOtherEnvelopes()
        assertNoOtherRawRequests()
    }

    data class RelayResponse(val request: RecordedRequest, val response: MockResponse) {

        /** Request parsed as envelope. */
        val envelope: SentryEnvelope? by lazy {
            try {
                EnvelopeReader(Sentry.getCurrentHub().options.serializer)
                    .read(GZIPInputStream(request.body.inputStream()))
            } catch (e: IOException) {
                null
            }
        }

        /** Run [assertion] on this request parsed as an envelope. */
        fun assert(assertion: (asserter: EnvelopeAsserter) -> Unit) {
            // Parse the request to rebuild the original envelope. If it fails we throw an assertion error.
            envelope?.let {
                assertion(EnvelopeAsserter(it, response))
            } ?: throw AssertionError("Was unable to parse the request as an envelope: $request")
        }
    }
}
