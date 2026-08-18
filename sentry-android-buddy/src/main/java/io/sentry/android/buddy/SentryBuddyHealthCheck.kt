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
import java.util.Locale
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

@ApiStatus.Experimental
public data class BuddyHealthCheckFinding
public constructor(
  public val id: String,
  public val title: String,
  public val description: String,
  public val severity: Severity,
  public val currentValue: String? = null,
  public val suggestedValue: String? = null,
  public val kotlinSnippet: String? = null,
  public val link: String? = null,
)

@ApiStatus.Experimental
public data class BuddyHealthCheckResponse
public constructor(
  public val summary: String,
  public val findings: List<BuddyHealthCheckFinding>,
)

@ApiStatus.Experimental
public interface SentryBuddyHealthCheckApi {
  public fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse
}

@ApiStatus.Experimental
public object DummySentryBuddyHealthCheckApi : SentryBuddyHealthCheckApi {
  override fun check(request: BuddyHealthCheckRequest): BuddyHealthCheckResponse {
    val findings = buildFindings(request).take(MAX_FINDINGS)
    val summary =
      if (findings.isEmpty()) {
        "Buddy did not find any obvious Sentry config changes to recommend."
      } else {
        "Buddy found ${findings.size} ${"finding".pluralize(findings.size)} worth checking."
      }
    return BuddyHealthCheckResponse(summary = summary, findings = findings)
  }

  private fun buildFindings(request: BuddyHealthCheckRequest): List<BuddyHealthCheckFinding> {
    val findings = mutableListOf<BuddyHealthCheckFinding>()
    val config = request.config
    val sdkVersion = parseSdkVersion(request.sdk)

    if (!config.dsnConfigured) {
      findings +=
        BuddyHealthCheckFinding(
          id = "dsn-missing",
          title = "Configure a DSN",
          description =
            "The SDK is initialized without a DSN, so Buddy cannot correlate this app with a Sentry project.",
          severity = Severity.HIGH,
          currentValue = "Missing",
          suggestedValue = "Set options.dsn",
          kotlinSnippet = "options.dsn = \"https://examplePublicKey@o0.ingest.sentry.io/0\"",
        )
    }

    if (sdkVersion != null && isOutdatedVersion(sdkVersion, BuildConfig.VERSION_NAME)) {
      findings +=
        BuddyHealthCheckFinding(
          id = "sdk-outdated",
          title = "Upgrade the Sentry SDK",
          description =
            "This app is using $sdkVersion, but Buddy is running against ${BuildConfig.VERSION_NAME}. Newer SDK versions usually include tracing, replay, and stability fixes.",
          severity = Severity.LOW,
          currentValue = sdkVersion,
          suggestedValue = BuildConfig.VERSION_NAME,
          link =
            "https://github.com/getsentry/sentry-java/releases/tag/${BuildConfig.VERSION_NAME}",
        )
    }

    if (config.tracesSampleRate == null && !config.hasTracesSampler) {
      findings +=
        BuddyHealthCheckFinding(
          id = "tracing-disabled",
          title = "Turn on tracing for performance visibility",
          description =
            "Buddy could not find a traces sample rate or traces sampler, so transaction tracing is likely off.",
          severity = Severity.MEDIUM,
          currentValue = "Tracing disabled",
          suggestedValue = "Set tracesSampleRate or tracesSampler",
          kotlinSnippet = "options.tracesSampleRate = 1.0",
        )
    }

    if (!config.sessionReplayEnabled && !config.sessionReplayOnErrorEnabled) {
      findings +=
        BuddyHealthCheckFinding(
          id = "replay-disabled",
          title = "Consider enabling Session Replay",
          description =
            "Replay is off for both full sessions and error-triggered captures, so visual debugging context is unavailable.",
          severity = Severity.LOW,
          currentValue = "Disabled",
          suggestedValue = "Set sessionReplay.sessionSampleRate or onErrorSampleRate",
          kotlinSnippet =
            "options.sessionReplay.sessionSampleRate = 0.1\n" +
              "options.sessionReplay.onErrorSampleRate = 1.0",
        )
    }

    if (config.anrEnabled == false) {
      findings +=
        BuddyHealthCheckFinding(
          id = "anr-disabled",
          title = "Enable ANR reporting",
          description =
            "Android ANR detection is turned off, so app hangs will be harder to diagnose in Sentry.",
          severity = Severity.LOW,
          currentValue = "Disabled",
          suggestedValue = "Set anrEnabled = true",
          kotlinSnippet = "options.isAnrEnabled = true",
        )
    }

    return findings
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

private fun String.pluralize(count: Int): String = if (count == 1) this else "${this}s"

private const val MAX_FINDINGS = 5

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
    writer.name("dsnConfigured").value(value.dsnConfigured)
    writer.name("release").value(value.release)
    writer.name("environment").value(value.environment)
    writer.name("dist").value(value.dist)
    writer.name("sampleRate").value(value.sampleRate)
    writer.name("tracesSampleRate").value(value.tracesSampleRate)
    writer.name("hasTracesSampler").value(value.hasTracesSampler)
    writer.name("profilesSampleRate").value(value.profilesSampleRate)
    writer.name("profilingEnabled").value(value.profilingEnabled)
    writer.name("autoSessionTrackingEnabled").value(value.autoSessionTrackingEnabled)
    writer.name("attachStacktrace").value(value.attachStacktrace)
    writer.name("beforeSendConfigured").value(value.beforeSendConfigured)
    writer.name("beforeSendTransactionConfigured").value(value.beforeSendTransactionConfigured)
    writer.name("beforeBreadcrumbConfigured").value(value.beforeBreadcrumbConfigured)
    writer.name("sessionReplaySampleRate").value(value.sessionReplaySampleRate)
    writer.name("sessionReplayOnErrorSampleRate").value(value.sessionReplayOnErrorSampleRate)
    writer.name("sessionReplayEnabled").value(value.sessionReplayEnabled)
    writer.name("sessionReplayOnErrorEnabled").value(value.sessionReplayOnErrorEnabled)
    writer.name("sessionReplayMaskAllText").value(value.sessionReplayMaskAllText)
    writer.name("sessionReplayMaskAllImages").value(value.sessionReplayMaskAllImages)
    writer.name("anrEnabled").value(value.anrEnabled)
    writer.name("attachScreenshot").value(value.attachScreenshot)
    writer.name("attachViewHierarchy").value(value.attachViewHierarchy)
    writer
      .name("autoActivityLifecycleTracingEnabled")
      .value(value.autoActivityLifecycleTracingEnabled)
    writer
      .name("activityLifecycleBreadcrumbsEnabled")
      .value(value.activityLifecycleBreadcrumbsEnabled)
    writer.name("appLifecycleBreadcrumbsEnabled").value(value.appLifecycleBreadcrumbsEnabled)
    writer.name("networkEventBreadcrumbsEnabled").value(value.networkEventBreadcrumbsEnabled)
    writer.name("framesTrackingEnabled").value(value.framesTrackingEnabled)
    writer.name("performanceV2Enabled").value(value.performanceV2Enabled)
    writer.name("ndkEnabled").value(value.ndkEnabled)
    writer.name("reportHistoricalAnrs").value(value.reportHistoricalAnrs)
    writer.name("attachAnrThreadDump").value(value.attachAnrThreadDump)
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
    var summary: String? = null
    var findings: List<BuddyHealthCheckFinding> = emptyList()

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "summary" -> summary = reader.nextStringOrNull()
        "findings" ->
          findings = reader.nextListOrNull(logger, HealthCheckFindingDeserializer).orEmpty()
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return BuddyHealthCheckResponse(
      summary = requireNotNull(summary) { "summary is required" },
      findings = findings,
    )
  }
}

internal object HealthCheckFindingDeserializer : JsonDeserializer<BuddyHealthCheckFinding> {
  override fun deserialize(
    reader: ObjectReader,
    logger: io.sentry.ILogger,
  ): BuddyHealthCheckFinding {
    var id: String? = null
    var title: String? = null
    var description: String? = null
    var severity: Severity = Severity.MEDIUM
    var currentValue: String? = null
    var suggestedValue: String? = null
    var kotlinSnippet: String? = null
    var link: String? = null

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "id" -> id = reader.nextStringOrNull()
        "title" -> title = reader.nextStringOrNull()
        "description" -> description = reader.nextStringOrNull()
        "severity" ->
          severity =
            reader.nextStringOrNull()?.uppercase(Locale.ROOT)?.let { Severity.valueOf(it) }
              ?: severity
        "currentValue",
        "current_value" -> currentValue = reader.nextStringOrNull()
        "suggestedValue",
        "suggested_value" -> suggestedValue = reader.nextStringOrNull()
        "kotlinSnippet",
        "kotlin_snippet" -> kotlinSnippet = reader.nextStringOrNull()
        "link" -> link = reader.nextStringOrNull()
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return BuddyHealthCheckFinding(
      id = requireNotNull(id) { "finding id is required" },
      title = requireNotNull(title) { "finding title is required" },
      description = requireNotNull(description) { "finding description is required" },
      severity = severity,
      currentValue = currentValue,
      suggestedValue = suggestedValue,
      kotlinSnippet = kotlinSnippet,
      link = link,
    )
  }
}
