package io.sentry.systemtest.util

import okhttp3.Request

class SentryMockServerClient(private val baseUrl: String) : LoggingInsecureRestClient() {

    fun getEnvelopeCount(): EnvelopeCounts {
        val request = Request.Builder()
            .url("$baseUrl/envelope-count")

        return callTyped(request, false)!!
    }

    fun reset() {
        val request = Request.Builder()
            .url("$baseUrl/reset")

        call(request, false)
    }

    fun getEnvelopes(): EnvelopesReceived {
        val request = Request.Builder()
            .url("$baseUrl/envelopes-received")

        return callTyped(request, false)!!
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
