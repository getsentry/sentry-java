package io.sentry.systemtest.util

import io.sentry.systemtest.Person
import io.sentry.systemtest.Todo
import okhttp3.Request
import okhttp3.RequestBody
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpStatusCodeException

class RestTestClient(private val backendBaseUrl: String) : LoggingInsecureRestClient() {

    fun getPerson(id: Long): Person? {
        val request = Request.Builder()
            .url("$backendBaseUrl/person/$id")

        return callTyped(request, true)
    }

    fun createPerson(person: Person, extraHeaders: Map<String, String>? = null): Person? {
        val request = Request.Builder()
            .url("$backendBaseUrl/person/")
            .post(toRequestBody(person))

        return callTyped(request, true, extraHeaders)
    }

    fun getPersonDistributedTracing(id: Long, extraHeaders: Map<String, String>? = null): Person? {
        val request = Request.Builder()
            .url("$backendBaseUrl/tracing/$id")

        return callTyped(request, true, extraHeaders)
    }

    fun createPersonDistributedTracing(person: Person, extraHeaders: Map<String, String>? = null): Person? {
        val request = Request.Builder()
            .url("$backendBaseUrl/tracing/")
            .post(toRequestBody(person))

        return callTyped(request, true, extraHeaders)
    }

    fun getTodo(id: Long): Todo? {
        val request = Request.Builder()
            .url("$backendBaseUrl/todo/$id")

        return callTyped(request, true)
    }

    fun getTodoWebclient(id: Long): Todo? {
        val request = Request.Builder()
            .url("$backendBaseUrl/todo-webclient/$id")

        return callTyped(request, true)
    }

    fun getTodoRestClient(id: Long): Todo? {
        val request = Request.Builder()
            .url("$backendBaseUrl/todo-restclient/$id")

        return callTyped(request, true)
    }
}
