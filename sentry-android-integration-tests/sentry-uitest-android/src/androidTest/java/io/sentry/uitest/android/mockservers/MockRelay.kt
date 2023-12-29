package io.sentry.uitest.android.mockservers

import androidx.test.espresso.idling.CountingIdlingResource
import io.sentry.uitest.android.waitUntilIdle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.assertNotNull

/** Mocks a relay server. */
class MockRelay(
    var waitForRequests: Boolean,
    private val relayIdlingResource: CountingIdlingResource
) {

    /** Mocks a relay server. */
    private val relay = MockWebServer()

    private val dsnProject = "1234"
    private val envelopePath = "/api/$dsnProject/envelope/"

    /** List of unasserted requests sent to the [envelopePath]. */
    private val unassertedEnvelopes = mutableListOf<RelayAsserter.RelayResponse>()

    /** List of responses to return when a request is sent. */
    private val responses = mutableListOf<(RecordedRequest) -> MockResponse?>()

    /** Set to check already received envelopes, to avoid duplicates due to e.g. retrying. */
    private val receivedEnvelopes: MutableSet<String> = HashSet()

    init {
        relay.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // If a request with a body size of 0 is received, we drop it.
                // This shouldn't happen in reality, but it rarely happens in tests.
                if (request.bodySize == 0L || request.failure != null) {
                    return MockResponse()
                }
                // We check if there is any custom response previously set to return to this request,
                // otherwise we return a successful MockResponse.
                val response = responses.asSequence()
                    .mapNotNull { it(request) }
                    .firstOrNull()
                    ?: MockResponse()

                // We should receive only envelopes on this path.
                if (request.path == envelopePath) {
                    val relayResponse = RelayAsserter.RelayResponse(request, response)
                    assertNotNull(relayResponse.envelope)
                    val envelopeId: String = relayResponse.envelope!!.header.eventId!!.toString()
                    // If we already received the envelope (e.g. retrying mechanism) we drop it
                    if (receivedEnvelopes.contains(envelopeId)) {
                        return MockResponse()
                    }
                    receivedEnvelopes.add(envelopeId)
                    unassertedEnvelopes.add(relayResponse)
                } else {
                    throw AssertionError("Expected $envelopePath, but the request path was ${request.path}")
                }

                // If we are waiting for requests to be received, we decrement the associated counter.
                if (waitForRequests) {
                    relayIdlingResource.decrement()
                }
                return response
            }
        }
    }

    /** Creates a dsn that will send request to this [MockRelay]. */
    fun createMockDsn() = "http://key@${relay.hostName}:${relay.port}/$dsnProject"

    /** Starts the mock relay server. */
    fun start() {
        receivedEnvelopes.clear()
        relay.start()
    }

    /** Shutdown the mock relay server and clear everything. */
    fun shutdown() {
        responses.clear()
        relay.shutdown()
    }

    /** Add a custom response to be returned at the next request received. */
    fun addResponse(response: (RecordedRequest) -> MockResponse?) {
        // Responses are added to the beginning of the list so they'll take precedence over
        // previously added ones.
        responses.add(0, response)
    }

    /** Add a custom response to be returned at the next request received, if it satisfies the [filter]. */
    fun addResponse(
        filter: (RecordedRequest) -> Boolean,
        responseBuilder: ((request: RecordedRequest, response: MockResponse) -> Unit)? = null
    ) {
        addResponse { request ->
            if (filter(request)) {
                MockResponse().also { response ->
                    responseBuilder?.invoke(request, response)
                }
            } else {
                null
            }
        }
    }

    /** Wait to receive all requests (if [waitForRequests] is true) and run the [assertion]. */
    fun assert(assertion: RelayAsserter.() -> Unit) {
        if (waitForRequests) {
            waitUntilIdle()
        }
        assertion(RelayAsserter(unassertedEnvelopes))
    }
}
