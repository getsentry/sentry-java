package io.sentry.android.buddy.bridge

import io.sentry.ILogger
import io.sentry.JsonDeserializer
import io.sentry.JsonObjectReader
import io.sentry.JsonSerializable
import io.sentry.JsonSerializer
import io.sentry.NoOpLogger
import io.sentry.ObjectReader
import io.sentry.ObjectWriter
import io.sentry.SentryOptions
import io.sentry.android.buddy.model.BuddyHealthCheckRequest
import io.sentry.android.buddy.model.BuddyHealthCheckResponse
import io.sentry.android.buddy.model.BuddySdkConfigSnapshot
import io.sentry.android.buddy.model.Recommendation
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
public class SentryBuddyHttpHealthCheckApi
@JvmOverloads
public constructor(
  private val baseUrl: String,
  private val client: OkHttpClient = OkHttpClient(),
) : SentryBuddyHealthCheckApi {
  private val json = JsonSerializer(SentryOptions())

  override fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse {
    val httpRequest =
      Request.Builder()
        .url(baseUrl.toHttpUrl().newBuilder().addPathSegments("v1/health-check").build())
        .post(serialize(request).toRequestBody(JSON_MEDIA_TYPE))
        .build()
    return execute(httpRequest)
  }

  private fun serialize(request: BuddyHealthCheckRequest): String {
    val writer = StringWriter()
    json.serialize(HealthCheckRequestPayload.from(request), writer)
    return writer.toString()
  }

  private fun execute(request: Request): BuddyHealthCheckResponse {
    try {
      client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IllegalStateException(response.errorMessage(body))
        }
        if (body.isBlank()) {
          throw IllegalStateException("Health check bridge returned an empty response.")
        }
        return parseResponse(body)
      }
    } catch (exception: IOException) {
      throw IllegalStateException(
        "Failed to call health check bridge: ${exception.message}",
        exception,
      )
    }
  }

  private fun parseResponse(body: String): BuddyHealthCheckResponse {
    try {
      return JsonObjectReader(StringReader(body)).use {
        HealthCheckResponseDeserializer.deserialize(it, NoOpLogger.getInstance())
      }
    } catch (exception: Exception) {
      throw IllegalStateException("Failed to parse health check bridge response.", exception)
    }
  }

  private fun Response.errorMessage(body: String): String {
    val error = extractError(body)
    return buildString {
      append("Health check bridge request failed with HTTP ").append(code)
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

private data class HealthCheckRequestPayload(
  val sdk: String,
  val config: BuddySdkConfigSnapshotPayload,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("sdk").value(sdk)
    writer.name("config")
    config.serialize(writer, logger)
    writer.endObject()
  }

  companion object {
    fun from(request: BuddyHealthCheckRequest): HealthCheckRequestPayload =
      HealthCheckRequestPayload(
        sdk = request.sdk,
        config = BuddySdkConfigSnapshotPayload.from(request.config),
      )
  }
}

private data class BuddySdkConfigSnapshotPayload(val value: BuddySdkConfigSnapshot) :
  JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: ILogger) {
    writer.beginObject()
    writer.name("dsn_configured").value(value.dsnConfigured)
    writer.name("release").value(value.release)
    writer.name("environment").value(value.environment)
    writer.name("dist").value(value.dist)
    writer.name("sample_rate").value(value.sampleRate)
    writer.name("traces_sample_rate").value(value.tracesSampleRate)
    writer.name("has_traces_sampler").value(value.hasTracesSampler)
    writer.name("profiles_sample_rate").value(value.profilesSampleRate)
    writer.name("profiling_enabled").value(value.profilingEnabled)
    writer.name("auto_session_tracking_enabled").value(value.autoSessionTrackingEnabled)
    writer.name("attach_stacktrace").value(value.attachStacktrace)
    writer.name("before_send_configured").value(value.beforeSendConfigured)
    writer.name("before_send_transaction_configured").value(value.beforeSendTransactionConfigured)
    writer.name("before_breadcrumb_configured").value(value.beforeBreadcrumbConfigured)
    writer.name("session_replay_sample_rate").value(value.sessionReplaySampleRate)
    writer.name("session_replay_on_error_sample_rate").value(value.sessionReplayOnErrorSampleRate)
    writer.name("session_replay_enabled").value(value.sessionReplayEnabled)
    writer.name("session_replay_on_error_enabled").value(value.sessionReplayOnErrorEnabled)
    writer.name("session_replay_mask_all_text").value(value.sessionReplayMaskAllText)
    writer.name("session_replay_mask_all_images").value(value.sessionReplayMaskAllImages)
    writer.name("anr_enabled").value(value.anrEnabled)
    writer.name("attach_screenshot").value(value.attachScreenshot)
    writer.name("attach_view_hierarchy").value(value.attachViewHierarchy)
    writer
      .name("auto_activity_lifecycle_tracing_enabled")
      .value(value.autoActivityLifecycleTracingEnabled)
    writer
      .name("activity_lifecycle_breadcrumbs_enabled")
      .value(value.activityLifecycleBreadcrumbsEnabled)
    writer.name("app_lifecycle_breadcrumbs_enabled").value(value.appLifecycleBreadcrumbsEnabled)
    writer.name("network_event_breadcrumbs_enabled").value(value.networkEventBreadcrumbsEnabled)
    writer.name("frames_tracking_enabled").value(value.framesTrackingEnabled)
    writer.name("performance_v2_enabled").value(value.performanceV2Enabled)
    writer.name("ndk_enabled").value(value.ndkEnabled)
    writer.name("report_historical_anrs").value(value.reportHistoricalAnrs)
    writer.name("attach_anr_thread_dump").value(value.attachAnrThreadDump)
    writer.endObject()
  }

  companion object {
    fun from(snapshot: BuddySdkConfigSnapshot): BuddySdkConfigSnapshotPayload =
      BuddySdkConfigSnapshotPayload(snapshot)
  }
}

internal object HealthCheckResponseDeserializer : JsonDeserializer<BuddyHealthCheckResponse> {
  override fun deserialize(
    reader: ObjectReader,
    logger: ILogger,
  ): BuddyHealthCheckResponse {
    var recommendations: List<Recommendation> = emptyList()

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "recommendations" ->
          recommendations = reader.nextListOrNull(logger, RecommendationDeserializer).orEmpty()

        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return BuddyHealthCheckResponse(recommendations)
  }
}
