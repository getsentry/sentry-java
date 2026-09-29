package io.sentry.android.buddy.bridge

import io.sentry.JsonObjectReader
import io.sentry.JsonSerializer
import io.sentry.SentryOptions
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public class SentryBuddyHttpLogIngestApi
@JvmOverloads
public constructor(
  private val baseUrl: String,
  private val client: OkHttpClient = OkHttpClient(),
) : SentryBuddyLogIngestApi {
  private val json = JsonSerializer(SentryOptions())

  override fun ingest(sessionId: String, items: List<BuddyLogItem>): Int {
    val httpRequest =
      Request.Builder()
        .url(baseUrl.toHttpUrl().newBuilder().addPathSegments("v1/logs/$sessionId").build())
        .post(serialize(items).toRequestBody(JSON_MEDIA_TYPE))
        .build()
    return execute(httpRequest)
  }

  private fun serialize(items: List<BuddyLogItem>): String {
    val writer = StringWriter()
    json.serialize(items, writer)
    return writer.toString()
  }

  private fun execute(request: Request): Int {
    try {
      client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IllegalStateException(response.errorMessage(body))
        }
        return extractStored(body)
      }
    } catch (exception: IOException) {
      throw IllegalStateException(
        "Failed to call logs bridge: ${exception.message}",
        exception,
      )
    }
  }

  private fun Response.errorMessage(body: String): String {
    val error = extractError(body)
    return buildString {
      append("Logs bridge request failed with HTTP ").append(code)
      error?.let { append(": ").append(it) }
    }
  }

  private fun extractStored(body: String): Int {
    if (body.isBlank()) {
      return 0
    }
    return try {
      ((JsonObjectReader(StringReader(body)).use { it.nextObjectOrNull() } as? Map<*, *>)?.get(
          "stored"
        ) as? Number)
        ?.toInt() ?: 0
    } catch (_: Exception) {
      0
    }
  }

  private fun extractError(body: String): String? {
    if (body.isBlank()) {
      return null
    }
    return try {
      (JsonObjectReader(StringReader(body)).use { it.nextObjectOrNull() } as? Map<*, *>)
        ?.get("error")
        ?.toString()
    } catch (_: Exception) {
      body.take(200)
    }
  }

  private companion object {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }
}
