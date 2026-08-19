package io.sentry.android.buddy.bridge

import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.buddy.BuildConfig
import io.sentry.android.buddy.model.BuddyHealthCheckRequest
import io.sentry.android.buddy.model.BuddyHealthCheckResponse
import io.sentry.android.buddy.model.BuddySdkConfigSnapshot
import io.sentry.android.buddy.model.Recommendation
import io.sentry.android.buddy.model.RecommendationAction
import io.sentry.android.buddy.model.Severity
import org.jetbrains.annotations.ApiStatus

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
          actions =
            listOf(
              RecommendationAction(
                id = "dsn-missing:fix",
                actionLabel = "Set the DSN",
                actionableForSeer = true,
                description =
                  "Set options.dsn in the Sentry SDK options so events reach a Sentry project.",
              )
            ),
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
          actions =
            listOf(
              RecommendationAction(
                id = "sdk-outdated:fix",
                actionLabel = "Upgrade the SDK",
                actionableForSeer = true,
                description =
                  "Raise the io.sentry dependency to the newest release and rebuild the app.",
              )
            ),
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
          actions =
            listOf(
              RecommendationAction(
                id = "tracing-disabled:fix",
                actionLabel = "Turn on tracing",
                actionableForSeer = true,
                description =
                  "Set options.tracesSampleRate, or install a tracesSampler, so transactions are recorded.",
              )
            ),
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
          actions =
            listOf(
              RecommendationAction(
                id = "replay-disabled:fix",
                actionLabel = "Turn on Session Replay",
                actionableForSeer = true,
                description =
                  "Set a sessionSampleRate or an onErrorSampleRate in options.sessionReplay.",
              )
            ),
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
          actions =
            listOf(
              RecommendationAction(
                id = "anr-disabled:fix",
                actionLabel = "Turn on ANR reporting",
                actionableForSeer = true,
                description =
                  "Set options.isAnrEnabled to true so app hangs are reported to Sentry.",
              )
            ),
        )
    }

    return recommendations
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
