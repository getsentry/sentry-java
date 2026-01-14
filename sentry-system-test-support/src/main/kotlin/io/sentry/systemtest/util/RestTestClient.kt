package io.sentry.systemtest.util

import io.sentry.systemtest.Person
import io.sentry.systemtest.Todo
import okhttp3.Request

class RestTestClient(private val backendBaseUrl: String) : LoggingInsecureRestClient() {
  fun getPerson(id: Long): Person? {
    val request = Request.Builder().url("$backendBaseUrl/person/$id")

    return callTyped(request, true)
  }

  fun createPerson(person: Person, extraHeaders: Map<String, String>? = null): Person? {
    val request = Request.Builder().url("$backendBaseUrl/person/").post(toRequestBody(person))

    return callTyped(request, true, extraHeaders)
  }

  fun getPersonDistributedTracing(id: Long, extraHeaders: Map<String, String>? = null): Person? {
    val request = Request.Builder().url("$backendBaseUrl/tracing/$id")

    return callTyped(request, true, extraHeaders)
  }

  fun createPersonDistributedTracing(
    person: Person,
    extraHeaders: Map<String, String>? = null,
  ): Person? {
    val request = Request.Builder().url("$backendBaseUrl/tracing/").post(toRequestBody(person))

    return callTyped(request, true, extraHeaders)
  }

  fun getTodo(id: Long): Todo? {
    val request = Request.Builder().url("$backendBaseUrl/todo/$id")

    return callTyped(request, true)
  }

  fun getTodoWebclient(id: Long): Todo? {
    val request = Request.Builder().url("$backendBaseUrl/todo-webclient/$id")

    return callTyped(request, true)
  }

  fun getTodoRestClient(id: Long): Todo? {
    val request = Request.Builder().url("$backendBaseUrl/todo-restclient/$id")

    return callTyped(request, true)
  }

  fun checkFeatureFlag(flagKey: String): FeatureFlagResponse? {
    val request = Request.Builder().url("$backendBaseUrl/feature-flag/check/$flagKey")

    return callTyped(request, true)
  }

  fun errorWithFeatureFlag(flagKey: String): String? {
    val request = Request.Builder().url("$backendBaseUrl/feature-flag/error/$flagKey")

    val response = call(request, true)
    return response?.body?.string()
  }

  fun getCountMetric(): String? {
    val request = Request.Builder().url("$backendBaseUrl/metric/count")

    return callTyped(request, true)
  }

  fun getGaugeMetric(value: Long): String? {
    val request = Request.Builder().url("$backendBaseUrl/metric/gauge/$value")

    return callTyped(request, true)
  }

  fun getDistributionMetric(value: Long): String? {
    val request = Request.Builder().url("$backendBaseUrl/metric/distribution/$value")

    return callTyped(request, true)
  }
}

data class FeatureFlagResponse(val flagKey: String, val value: Boolean)
