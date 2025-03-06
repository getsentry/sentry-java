package io.sentry.systemtest.util

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory


open class LoggingInsecureRestClient {
    val logger = LoggerFactory.getLogger(LoggingInsecureRestClient::class.java)
    var lastKnownStatusCode: Int? = null

    protected inline fun <reified T> callTyped(requestBuilder: Request.Builder, useAuth: Boolean, extraHeaders: Map<String, String>? = null): T? {
        val response = call(requestBuilder, useAuth, extraHeaders)
        val responseBody = response?.body?.string()
        if (response?.isSuccessful != true) {
            return null
        }
        return responseBody?.let { objectMapper().readValue(it, T::class.java) }
    }

    protected fun call(requestBuilder: Request.Builder, useAuth: Boolean, extraHeaders: Map<String, String>? = null): Response? {
        try {
            val request = requestBuilder.also { originalRequest ->
                var modifiedRequest = originalRequest

                if (useAuth) {
                    modifiedRequest = modifiedRequest.header(
                        "Authorization",
                        Credentials.basic("user", "password")
                    )
                }

                extraHeaders?.forEach { key, value ->
                    modifiedRequest = modifiedRequest.header(key, value)
                }

                modifiedRequest
            }.build()
            val call = client().newCall(request)
            val response = call.execute()
            lastKnownStatusCode = response.code
            return response
        } catch (e: Exception) {
            lastKnownStatusCode = null
            logger.error("Request failed", e)
            return null
        }
    }

    protected fun client(): OkHttpClient {
        return OkHttpClient.Builder()
            .build()
    }

    protected fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper()
    }

    protected fun toRequestBody(o: Any?): RequestBody {
        val stringValue = objectMapper().writeValueAsString(o)
        return stringValue.toRequestBody("application/json; charset=utf-8".toMediaType())
    }
}
