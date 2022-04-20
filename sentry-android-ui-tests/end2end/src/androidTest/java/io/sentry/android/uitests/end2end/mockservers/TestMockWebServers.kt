package io.sentry.android.uitests.end2end.mockservers

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** Interface exposing mock webservers */
class TestMockWebServers {

    /** Mocks a relay server. */
    val relay = MockWebServer()

    init {
        relay.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                request.path
                return MockResponse()
            }
        }
    }
}
