package io.sentry.android.buddy

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public enum class BuddyFlowImportance(public val value: String) {
  LOW("low"),
  MEDIUM("medium"),
  HIGH("high"),
  BUSINESS_CRITICAL("business_critical"),
}
