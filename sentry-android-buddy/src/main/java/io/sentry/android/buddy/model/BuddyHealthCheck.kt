package io.sentry.android.buddy.model

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
