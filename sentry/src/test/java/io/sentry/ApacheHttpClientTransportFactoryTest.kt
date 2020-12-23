package io.sentry

import com.nhaarman.mockitokotlin2.mock
import kotlin.test.Test
import kotlin.test.assertNotNull

class ApacheHttpClientTransportFactoryTest {

    @Test
    fun `creates ApacheHttpClientTransport`() {
        val factory = ApacheHttpClientTransportFactory()
        val options = SentryOptions().apply {
            setLogger(mock())
            setSerializer(mock())
        }
        val requestDetails = mock<RequestDetails>()

        val transport = factory.create(options, requestDetails)
        assertNotNull(transport)
    }
}
