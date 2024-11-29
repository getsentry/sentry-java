package io.sentry.systemtest.util

import io.sentry.samples.spring.boot.Person
import io.sentry.samples.spring.boot.Todo
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpStatusCodeException

class RestTestClient(private val backendBaseUrl: String) : LoggingInsecureRestClient() {
    var lastKnownStatusCode: HttpStatus? = null

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

    fun createPerson(person: Person, extraHeaders: Map<String, String>? = null): Person? {
        return try {
            val response = restTemplate().exchange("$backendBaseUrl/person/", HttpMethod.POST, entityWithAuth(person, extraHeaders), Person::class.java, person)
            lastKnownStatusCode = response.statusCode
            response.body
        } catch (e: HttpStatusCodeException) {
            lastKnownStatusCode = e.statusCode
            null
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

    private fun entityWithAuth(request: Any? = null, extraHeaders: Map<String, String>? = null): HttpEntity<Any?> {
        val headers = HttpHeaders().also { httpHeaders ->
            httpHeaders.setBasicAuth("user", "password")
            extraHeaders?.forEach { key, value ->
                httpHeaders.set(key, value)
            }
        }

        return HttpEntity<Any?>(request, headers)
    }
}
