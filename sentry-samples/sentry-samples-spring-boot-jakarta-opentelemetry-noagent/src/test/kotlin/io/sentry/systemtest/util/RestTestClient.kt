package io.sentry.systemtest.util

import io.sentry.samples.spring.boot.jakarta.Person
import io.sentry.samples.spring.boot.jakarta.Todo
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.HttpStatusCodeException

class RestTestClient(private val backendBaseUrl: String) : LoggingInsecureRestClient() {
    var lastKnownStatusCode: HttpStatusCode? = null

    fun getPerson(id: Long): Person? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/person/{id}", HttpMethod.GET, entityWithAuth(), Person::class.java, mapOf("id" to id))
            lastKnownStatusCode = response.statusCode
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = e.statusCode
            null
        }
    }

    fun createPerson(person: Person): Person? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/person/", HttpMethod.POST, entityWithAuth(person), Person::class.java, person)
            lastKnownStatusCode = response.statusCode
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = e.statusCode
            null
        }
    }

    fun getPersonDistributedTracing(id: Long, sentryTraceHeader: String? = null, baggageHeader: String? = null): Person? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/tracing/{id}", HttpMethod.GET, entityWithAuth(headerCallback = tracingHeaders(sentryTraceHeader, baggageHeader)), Person::class.java, mapOf("id" to id))
            lastKnownStatusCode = response.statusCode
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = e.statusCode
            null
        }
    }

    fun createPersonDistributedTracing(person: Person, sentryTraceHeader: String? = null, baggageHeader: String? = null): Person? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/tracing/", HttpMethod.POST, entityWithAuth(person, tracingHeaders(sentryTraceHeader, baggageHeader)), Person::class.java, person)
            lastKnownStatusCode = response.statusCode
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = e.statusCode
            null
        }
    }

    private fun tracingHeaders(sentryTraceHeader: String?, baggageHeader: String?): (HttpHeaders) -> HttpHeaders {
        return { httpHeaders ->
            sentryTraceHeader?.let { httpHeaders.set("sentry-trace", it) }
            baggageHeader?.let { httpHeaders.set("baggage", it) }
            httpHeaders
        }
    }

    fun getTodo(id: Long): Todo? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/todo/{id}", HttpMethod.GET, entityWithAuth(), Todo::class.java, mapOf("id" to id))
            lastKnownStatusCode = response.statusCode
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = e.statusCode
            null
        }
    }

    fun getTodoWebclient(id: Long): Todo? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/todo-webclient/{id}", HttpMethod.GET, entityWithAuth(), Todo::class.java, mapOf("id" to id))
            lastKnownStatusCode = response.statusCode
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = e.statusCode
            null
        }
    }

    fun getTodoRestClient(id: Long): Todo? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/todo-restclient/{id}", HttpMethod.GET, entityWithAuth(), Todo::class.java, mapOf("id" to id))
            lastKnownStatusCode = response.statusCode
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = e.statusCode
            null
        }
    }

    private fun entityWithAuth(request: Any? = null, headerCallback: ((HttpHeaders) -> HttpHeaders)? = null): HttpEntity<Any?> {
        val headers = HttpHeaders().also {
            it.setBasicAuth("user", "password")
        }

        val modifiedHeaders = headerCallback?.invoke(headers) ?: headers

        return HttpEntity<Any?>(request, modifiedHeaders)
    }
}
