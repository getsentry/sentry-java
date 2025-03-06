package io.sentry.systemtest.util

import io.sentry.systemtest.Person
import io.sentry.systemtest.Todo
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpStatusCodeException

class RestTestClient(private val backendBaseUrl: String) : LoggingInsecureRestClient() {
    var lastKnownStatusCode: Int? = null

    fun getPerson(id: Long): Person? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/person/{id}", HttpMethod.GET, entityWithAuth(), Person::class.java, mapOf("id" to id))
            lastKnownStatusCode = statusCode(response)
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = statusCode(e)
            null
        }
    }

    fun createPerson(person: Person, extraHeaders: Map<String, String>? = null): Person? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/person/", HttpMethod.POST, entityWithAuth(person, extraHeaders), Person::class.java, person)
            lastKnownStatusCode = statusCode(response)
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = statusCode(e)
            null
        }
    }

    fun getPersonDistributedTracing(id: Long, extraHeaders: Map<String, String>? = null): Person? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/tracing/{id}", HttpMethod.GET, entityWithAuth(extraHeaders = extraHeaders), Person::class.java, mapOf("id" to id))
            lastKnownStatusCode = statusCode(response)
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = statusCode(e)
            null
        }
    }

    fun createPersonDistributedTracing(person: Person, extraHeaders: Map<String, String>? = null): Person? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/tracing/", HttpMethod.POST, entityWithAuth(person, extraHeaders), Person::class.java, person)
            lastKnownStatusCode = statusCode(response)
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = statusCode(e)
            null
        }
    }

    fun getTodo(id: Long): Todo? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/todo/{id}", HttpMethod.GET, entityWithAuth(), Todo::class.java, mapOf("id" to id))
            lastKnownStatusCode = statusCode(response)
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = statusCode(e)
            null
        }
    }

    fun getTodoWebclient(id: Long): Todo? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/todo-webclient/{id}", HttpMethod.GET, entityWithAuth(), Todo::class.java, mapOf("id" to id))
            lastKnownStatusCode = statusCode(response)
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = statusCode(e)
            null
        }
    }

    fun getTodoRestClient(id: Long): Todo? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/todo-restclient/{id}", HttpMethod.GET, entityWithAuth(), Todo::class.java, mapOf("id" to id))
            lastKnownStatusCode = statusCode(response)
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = statusCode(e)
            null
        }
    }

    private fun entityWithAuth(request: Any? = null, extraHeaders: Map<String, String>? = null): HttpEntity<Any?> {
        val headers = HttpHeaders().also {
            it.setBasicAuth("user", "password")
        }
        extraHeaders?.forEach { key, value -> headers.set(key, value) }

        return HttpEntity<Any?>(request, headers)
    }

    private fun statusCode(o: Any): Int? {
        val statusCodeValue = (o as? ResponseEntity<Any?>)?.statusCodeValue
        if (statusCodeValue != null) {
            return statusCodeValue
        }

        val errorStatusCodeValue = (o as? HttpStatusCodeException)?.rawStatusCode
        if (errorStatusCodeValue != null) {
            return errorStatusCodeValue
        }

        return null
    }
}
