package io.sentry.android.uitests.end2end.mockservers

import io.sentry.EnvelopeReader
import io.sentry.Sentry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.zip.GZIPInputStream

class RelayAsserter(
    private val unassertedEnvelopes: MutableList<RelayResponse>,
    private val unassertedRequests: MutableList<RelayResponse>
) {

    fun assertRawRequest(assertion: ((request: RecordedRequest, response: MockResponse) -> Unit)? = null) {
        val relayResponse = unassertedEnvelopes.removeFirstOrNull()
            ?: throw AssertionError("No envelope request found")
        assertion?.let {
            it(relayResponse.request, relayResponse.response)
        }
    }

    fun assertEnvelope(assertion: (asserter: EnvelopeAsserter) -> Unit) {
        assertRawRequest { request, response ->
            val envelope = EnvelopeReader(Sentry.getCurrentHub().options.serializer)
                .read(GZIPInputStream(request.body.inputStream()))
                ?: throw AssertionError("Was unable to parse the request as an envelope: $request")
            assertion(EnvelopeAsserter(envelope, response))
        }
    }

    fun assertNoOtherEnvelopes() {
        if (unassertedEnvelopes.isNotEmpty()) {
            throw AssertionError("There were other ${unassertedEnvelopes.size} envelope requests: $unassertedEnvelopes")
        }
    }

    fun assertNoOtherRequests() {
        assertNoOtherEnvelopes()
        if (unassertedRequests.isNotEmpty()) {
            throw AssertionError("There were other ${unassertedRequests.size} requests: $unassertedRequests")
        }
    }

    data class RelayResponse(val request: RecordedRequest, val response: MockResponse)
}
