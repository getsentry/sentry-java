package io.sentry.android.buddy.model

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public enum class Severity(public val value: String) {
  LOW("LOW"),
  MEDIUM("MEDIUM"),
  HIGH("HIGH"),
}
