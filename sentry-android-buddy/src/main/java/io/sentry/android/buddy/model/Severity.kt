package io.sentry.android.buddy.model

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public enum class Severity(public val value: String) {
  LOW("low"),
  MEDIUM("medium"),
  HIGH("high"),
}
