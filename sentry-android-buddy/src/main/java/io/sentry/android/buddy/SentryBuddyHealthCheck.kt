package io.sentry.android.buddy

import io.sentry.JsonDeserializer
import io.sentry.JsonObjectReader
import io.sentry.JsonSerializable
import io.sentry.JsonSerializer
import io.sentry.ObjectReader
import io.sentry.ObjectWriter
import io.sentry.Sentry
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
public data class BuddySdkConfigSnapshot
public constructor(
  public val dsnConfigured: Boolean,
  public val release: String? = null,
  public val environment: String? = null,
  public val dist: String? = null,
  public val sampleRate: Double? = null,
  public val tracesSampleRate: Double? = null,
  public val hasTracesSampler: Boolean = false,
  public val profilesSampleRate: Double? = null,
  public val profilingEnabled: Boolean = false,
  public val autoSessionTrackingEnabled: Boolean = false,
  public val attachStacktrace: Boolean = false,
  public val beforeSendConfigured: Boolean = false,
  public val beforeSendTransactionConfigured: Boolean = false,
  public val beforeBreadcrumbConfigured: Boolean = false,
  public val sessionReplaySampleRate: Double? = null,
  public val sessionReplayOnErrorSampleRate: Double? = null,
  public val sessionReplayEnabled: Boolean = false,
  public val sessionReplayOnErrorEnabled: Boolean = false,
  public val sessionReplayMaskAllText: Boolean = true,
  public val sessionReplayMaskAllImages: Boolean = true,
  public val anrEnabled: Boolean? = null,
  public val attachScreenshot: Boolean? = null,
  public val attachViewHierarchy: Boolean? = null,
  public val autoActivityLifecycleTracingEnabled: Boolean? = null,
  public val activityLifecycleBreadcrumbsEnabled: Boolean? = null,
  public val appLifecycleBreadcrumbsEnabled: Boolean? = null,
  public val networkEventBreadcrumbsEnabled: Boolean? = null,
  public val framesTrackingEnabled: Boolean? = null,
  public val performanceV2Enabled: Boolean? = null,
  public val ndkEnabled: Boolean? = null,
  public val reportHistoricalAnrs: Boolean? = null,
  public val attachAnrThreadDump: Boolean? = null,
)

@ApiStatus.Experimental
public data class BuddyHealthCheckRequest
public constructor(public val sdk: String, public val config: BuddySdkConfigSnapshot)

/**
 * Mirrors `HealthCheckResponse` of the bridge, which answers with recommendations only. The
 * recommendation type is shared with the flow analysis endpoint.
 */
@ApiStatus.Experimental
public data class BuddyHealthCheckResponse
@JvmOverloads
public constructor(public val recommendations: List<Recommendation> = emptyList())

@ApiStatus.Experimental
public interface SentryBuddyHealthCheckApi {
  public fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse
}

@ApiStatus.Experimental
public object DummySentryBuddyHealthCheckApi : SentryBuddyHealthCheckApi {
  override fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse =
    BuddyHealthCheckResponse(buildRecommendations(request).take(MAX_RECOMMENDATIONS))

  private fun buildRecommendations(request: BuddyHealthCheckRequest): List<Recommendation> {
    val recommendations = mutableListOf<Recommendation>()
    val config = request.config
    val sdkVersion = parseSdkVersion(request.sdk)

    if (!config.dsnConfigured) {
      recommendations +=
        Recommendation(
          id = "dsn-missing",
          title = "Configure a DSN",
          description =
            "The SDK is initialized without a DSN, so Buddy cannot correlate this app with a Sentry project. Set options.dsn.",
          severity = Severity.HIGH,
          resolvable = false,
        )
    }

    if (sdkVersion != null && isOutdatedVersion(sdkVersion, BuildConfig.VERSION_NAME)) {
      recommendations +=
        Recommendation(
          id = "sdk-outdated",
          title = "Upgrade Sentry SDK to ${BuildConfig.VERSION_NAME}",
          description =
            "This app is using $sdkVersion, but Buddy is running against ${BuildConfig.VERSION_NAME}. Newer SDK versions usually include tracing, replay, and stability fixes.",
          link =
            "https://github.com/getsentry/sentry-java/releases/tag/${BuildConfig.VERSION_NAME}",
          severity = Severity.LOW,
          resolvable = false,
        )
    }

    if (config.tracesSampleRate == null && !config.hasTracesSampler) {
      recommendations +=
        Recommendation(
          id = "tracing-disabled",
          title = "Turn on tracing for performance visibility",
          description =
            "Buddy could not find a traces sample rate or traces sampler, so transaction tracing is likely off. Set options.tracesSampleRate or options.tracesSampler.",
          severity = Severity.MEDIUM,
          resolvable = false,
        )
    }

    if (!config.sessionReplayEnabled && !config.sessionReplayOnErrorEnabled) {
      recommendations +=
        Recommendation(
          id = "replay-disabled",
          title = "Consider enabling Session Replay",
          description =
            "Replay is off for both full sessions and error-triggered captures, so visual debugging context is unavailable. Set options.sessionReplay.sessionSampleRate or onErrorSampleRate.",
          severity = Severity.LOW,
          resolvable = false,
        )
    }

    if (config.anrEnabled == false) {
      recommendations +=
        Recommendation(
          id = "anr-disabled",
          title = "Enable ANR reporting",
          description =
            "Android ANR detection is turned off, so app hangs will be harder to diagnose in Sentry. Set options.isAnrEnabled = true.",
          severity = Severity.LOW,
          resolvable = false,
        )
    }

    return recommendations
  }
}

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
        HealthCheckResponseDeserializer.deserialize(it, io.sentry.NoOpLogger.getInstance())
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

internal object BuddyHealthCheckCapture {
  fun captureRequest(): BuddyHealthCheckRequest {
    val options = Sentry.getCurrentScopes().options
    return BuddyHealthCheckRequest(sdk = options.sdkIdentifier(), config = options.toSnapshot())
  }
}

private fun SentryOptions.toSnapshot(): BuddySdkConfigSnapshot {
  val replay = sessionReplay
  return BuddySdkConfigSnapshot(
    dsnConfigured = !dsn.isNullOrBlank(),
    release = release,
    environment = environment,
    dist = dist,
    sampleRate = sampleRate,
    tracesSampleRate = tracesSampleRate,
    hasTracesSampler = tracesSampler != null,
    profilesSampleRate = profilesSampleRate,
    profilingEnabled = isProfilingEnabled,
    autoSessionTrackingEnabled = isEnableAutoSessionTracking,
    attachStacktrace = isAttachStacktrace,
    beforeSendConfigured = beforeSend != null,
    beforeSendTransactionConfigured = beforeSendTransaction != null,
    beforeBreadcrumbConfigured = beforeBreadcrumb != null,
    sessionReplaySampleRate = replay.sessionSampleRate,
    sessionReplayOnErrorSampleRate = replay.onErrorSampleRate,
    sessionReplayEnabled = replay.isSessionReplayEnabled,
    sessionReplayOnErrorEnabled = replay.isSessionReplayForErrorsEnabled,
    sessionReplayMaskAllText =
      replay.maskViewClasses.contains(io.sentry.SentryMaskingOptions.TEXT_VIEW_CLASS_NAME),
    sessionReplayMaskAllImages =
      replay.maskViewClasses.contains(io.sentry.SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME),
    anrEnabled = booleanOption("isAnrEnabled"),
    attachScreenshot = booleanOption("isAttachScreenshot"),
    attachViewHierarchy = booleanOption("isAttachViewHierarchy"),
    autoActivityLifecycleTracingEnabled = booleanOption("isEnableAutoActivityLifecycleTracing"),
    activityLifecycleBreadcrumbsEnabled = booleanOption("isEnableActivityLifecycleBreadcrumbs"),
    appLifecycleBreadcrumbsEnabled = booleanOption("isEnableAppLifecycleBreadcrumbs"),
    networkEventBreadcrumbsEnabled = booleanOption("isEnableNetworkEventBreadcrumbs"),
    framesTrackingEnabled = booleanOption("isEnableFramesTracking"),
    performanceV2Enabled = booleanOption("isEnablePerformanceV2"),
    ndkEnabled = booleanOption("isEnableNdk"),
    reportHistoricalAnrs = booleanOption("isReportHistoricalAnrs"),
    attachAnrThreadDump = booleanOption("isAttachAnrThreadDump"),
  )
}

private fun SentryOptions.booleanOption(methodName: String): Boolean? =
  runCatching {
      javaClass.getMethod(methodName).invoke(this)
    }
    .getOrNull() as? Boolean

private fun SentryOptions.sdkIdentifier(): String {
  val sdkVersion = sdkVersion
  if (sdkVersion != null) {
    return "${sdkVersion.name}@${sdkVersion.version}"
  }
  return sentryClientName?.takeIf { it.isNotBlank() } ?: "unknown"
}

private fun parseSdkVersion(sdk: String): String? =
  sdk.substringAfter('@', missingDelimiterValue = "").ifBlank { null }

private fun isOutdatedVersion(current: String, latest: String): Boolean {
  val currentParts = current.split('.').map { it.toIntOrNull() ?: 0 }
  val latestParts = latest.split('.').map { it.toIntOrNull() ?: 0 }
  val length = maxOf(currentParts.size, latestParts.size)
  for (index in 0 until length) {
    val currentPart = currentParts.getOrElse(index) { 0 }
    val latestPart = latestParts.getOrElse(index) { 0 }
    if (latestPart > currentPart) {
      return true
    }
    if (latestPart < currentPart) {
      return false
    }
  }
  return false
}

private const val MAX_RECOMMENDATIONS = 5

private data class HealthCheckRequestPayload(
  val sdk: String,
  val config: BuddySdkConfigSnapshotPayload,
) : JsonSerializable {
  @Throws(IOException::class)
  override fun serialize(writer: ObjectWriter, logger: io.sentry.ILogger) {
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
  override fun serialize(writer: ObjectWriter, logger: io.sentry.ILogger) {
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
    logger: io.sentry.ILogger,
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
