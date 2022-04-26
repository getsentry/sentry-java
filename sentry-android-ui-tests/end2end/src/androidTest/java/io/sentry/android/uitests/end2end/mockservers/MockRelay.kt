package io.sentry.android.uitests.end2end.mockservers

import androidx.test.espresso.idling.CountingIdlingResource
import io.sentry.android.uitests.end2end.BaseUiTest
import io.sentry.android.uitests.end2end.utils.BooleanIdlingResource
import io.sentry.android.uitests.end2end.waitUntilIdle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** Interface exposing mock webservers */
class MockRelay (
    var checkIdlingResources: Boolean,
    private val relayIdlingResource: CountingIdlingResource
) {

    /** Mocks a relay server. */
    private val relay = MockWebServer()

    val hostName
        get() = relay.hostName
    val port
        get() = relay.port

    val dsnProject = "1234"
    private val envelopePath = "/api/$dsnProject/envelope/"

    private val unassertedEnvelopes = mutableListOf<RelayAsserter.RelayResponse>()
    private val unassertedRequests = mutableListOf<RelayAsserter.RelayResponse>()

    private val responses = mutableListOf<(RecordedRequest) -> MockResponse?>()

    init {
        relay.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val response = responses.asSequence()
                    .mapNotNull { it(request) }
                    .firstOrNull()
                    ?: MockResponse().setResponseCode(404)
                dispatchSentRequest(RelayAsserter.RelayResponse(request, response))

                if (checkIdlingResources) {
                    relayIdlingResource.decrement()
                }
                return response
            }
        }
    }

    fun start() = relay.start()
    fun shutdown() {
        clearResponses()
        relay.shutdown()
    }

    fun addResponse(response: (RecordedRequest) -> MockResponse?) {
        // Responses are added to the beginning of the list so they'll take precedence over
        // previously added ones.
        responses.add(0, response)
    }

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

    fun clearResponses() {
        responses.clear()
    }

    fun assert(assertion: RelayAsserter.() -> Unit) {
        if (checkIdlingResources) {
            waitUntilIdle()
        }
        assertion(RelayAsserter(unassertedEnvelopes, unassertedRequests))
    }

    private fun dispatchSentRequest(relayResponse: RelayAsserter.RelayResponse) {
        when {
            relayResponse.request.path == envelopePath -> {
                unassertedEnvelopes.add(relayResponse)
            }
            else -> {
                unassertedRequests.add(relayResponse)
            }
        }
    }

    fun MockResponse.hasSucceed() = status.matches(".*2[\\d]{2} OK".toRegex())
}
