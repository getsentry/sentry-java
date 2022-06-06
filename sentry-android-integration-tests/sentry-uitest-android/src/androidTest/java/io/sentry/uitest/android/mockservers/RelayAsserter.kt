package io.sentry.uitest.android.mockservers

import io.sentry.EnvelopeReader
import io.sentry.Sentry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.zip.GZIPInputStream

/** Class used to assert requests sent to [MockRelay]. */
class RelayAsserter(
    private val unassertedEnvelopes: MutableList<RelayResponse>,
    private val unassertedRequests: MutableList<RelayResponse>
) {

    /**
     * Asserts an envelope request exists and allows to make other assertions on it and its response.
     * The asserted envelope request is then removed from internal list of unasserted envelope.
     */
    fun assertRawEnvelope(assertion: ((request: RecordedRequest, response: MockResponse) -> Unit)? = null) {
        val relayResponse = unassertedEnvelopes.removeFirstOrNull()
            ?: throw AssertionError("No envelope request found")
        assertion?.let {
            it(relayResponse.request, relayResponse.response)
        }
    }

    /**
     * Asserts a request exists and makes other assertions on it and its response.
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
     * Asserts a request exists, parses it as an envelope and makes other assertions through a [EnvelopeAsserter].
     * The asserted envelope is then removed from internal list of unasserted envelopes.
     */
    fun assertEnvelope(assertion: (asserter: EnvelopeAsserter) -> Unit) {
        assertRawEnvelope { request, response ->
            // Parse the request to rebuild the original envelope. If it fails we throw an assertion error.
            val envelope = EnvelopeReader(Sentry.getCurrentHub().options.serializer)
                .read(GZIPInputStream(request.body.inputStream()))
                ?: throw AssertionError("Was unable to parse the request as an envelope: $request")
            assertion(EnvelopeAsserter(envelope, response))
        }
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

    data class RelayResponse(val request: RecordedRequest, val response: MockResponse)
}
