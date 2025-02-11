package io.sentry.systemtest.util

import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod

class SentryMockServerClient(private val baseUrl: String) : LoggingInsecureRestClient() {

    fun getEnvelopeCount(): EnvelopeCounts {
        val response = restTemplate().exchange("$baseUrl/envelope-count", HttpMethod.GET, entityWithAuth(), EnvelopeCounts::class.java)
        return response.body!!
    }

    fun reset() {
        restTemplate().exchange("$baseUrl/reset", HttpMethod.GET, entityWithAuth(), Any::class.java)
    }

    fun getEnvelopes(): EnvelopesReceived {
        val response = restTemplate().exchange("$baseUrl/envelopes-received", HttpMethod.GET, entityWithAuth(), EnvelopesReceived::class.java)
        return response.body!!
    }

    private fun entityWithAuth(request: Any? = null): HttpEntity<Any?> {
        val headers = HttpHeaders()
        return HttpEntity<Any?>(request, headers)
    }
}

class EnvelopeCounts {
    val envelopes: Long? = null

    override fun toString(): String {
        return "EnvelopeCounts{envelopes=$envelopes}"
    }
}

class EnvelopesReceived {
    val envelopes: List<String>? = null

    override fun toString(): String {
        return "EnvelopesReceived{envelopes=$envelopes}"
    }
}
