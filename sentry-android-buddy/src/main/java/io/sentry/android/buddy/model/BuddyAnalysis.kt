package io.sentry.android.buddy.model

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public enum class BuddyFocusArea(public val label: String) {
  ERRORS_AND_CRASHES("Errors and crashes"),
  NETWORK_TIMING("Network timing"),
  MISSING_INSTRUMENTATION("Missing instrumentation"),
  FRAME_DROPS_AND_JANK("Frame drops and jank"),
}

@ApiStatus.Experimental
public data class BuddyInsight
public constructor(
  public val title: String,
  public val body: String,
  public val severity: Severity,
  public val elapsedMs: Long? = null,
)

@ApiStatus.Experimental
public data class BuddyAnalysisResponse
public constructor(
  public val summary: String,
  public val insights: List<BuddyInsight>,
  public val recommendations: List<Recommendation>,
)
