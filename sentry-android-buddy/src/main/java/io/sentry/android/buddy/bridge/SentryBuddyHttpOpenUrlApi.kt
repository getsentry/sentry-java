package io.sentry.android.buddy.bridge

import android.content.Context
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonSerializable
import io.sentry.JsonSerializer
import io.sentry.ObjectWriter
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
public class SentryBuddyHttpOpenUrlApi
@JvmOverloads
public constructor(
  private val baseUrl: String,
  private val client: OkHttpClient = OkHttpClient(),
) : SentryBuddyOpenUrlApi {
  private val json = JsonSerializer(SentryOptions())

  override fun open(context: Context, url: String) {
    val httpRequest =
      Request.Builder()
        .url(baseUrl.toHttpUrl().newBuilder().addPathSegments("v1/open-url").build())
        .post(serialize(OpenUrlRequest(url)).toRequestBody(JSON_MEDIA_TYPE))
        .build()
    execute(httpRequest)
  }

  private fun serialize(request: OpenUrlRequest): String {
    val writer = StringWriter()
    json.serialize(request, writer)
    return writer.toString()
  }

  private fun execute(request: Request) {
    try {
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          throw IllegalStateException(response.errorMessage())
        }
      }
    } catch (exception: IOException) {
      throw IllegalStateException(
        "Failed to call open URL bridge: ${exception.message}",
        exception,
      )
    }
  }

  private fun Response.errorMessage(): String {
    val error = extractError(body?.string().orEmpty())
    return buildString {
      append("Open URL bridge request failed with HTTP ").append(code)
      error?.let { append(": ").append(it) }
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

private data class OpenUrlRequest(val url: String) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("url").value(url)
    writer.endObject()
  }
}
