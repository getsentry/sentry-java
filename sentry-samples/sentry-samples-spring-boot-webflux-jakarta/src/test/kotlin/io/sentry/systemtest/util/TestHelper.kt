package io.sentry.systemtest.util

import com.apollographql.apollo3.api.ApolloResponse
import io.sentry.systemtest.graphql.GraphqlTestClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestHelper(backendUrl: String) {

    val restClient: RestTestClient
    val graphqlClient: GraphqlTestClient
    val sentryClient: SentryMockServerClient

    var envelopeCounts: EnvelopeCounts? = null

    init {
        restClient = RestTestClient(backendUrl)
        sentryClient = SentryMockServerClient("http://localhost:8000")
        graphqlClient = GraphqlTestClient(backendUrl)
    }

    fun snapshotEnvelopeCount() {
        envelopeCounts = sentryClient.getEnvelopeCount()
    }

    fun ensureEnvelopeCountIncreased() {
        Thread.sleep(1000)
        val envelopeCountsAfter = sentryClient.getEnvelopeCount()
        assertTrue(envelopeCountsAfter!!.envelopes!! > envelopeCounts!!.envelopes!!)
    }

    fun ensureNoErrors(response: ApolloResponse<Any?>?) {
        response ?: throw RuntimeException("no response")
        assertFalse(response.hasErrors())
    }

    fun ensureErrorCount(response: ApolloResponse<Any?>?, errorCount: Int) {
        response ?: throw RuntimeException("no response")
        assertEquals(errorCount, response.errors?.size)
    }
}
