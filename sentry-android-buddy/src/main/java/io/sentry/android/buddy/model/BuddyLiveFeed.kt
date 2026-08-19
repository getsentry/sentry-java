package io.sentry.android.buddy.model

import java.util.Date

internal data class BuddyLiveFeed(
  val items: List<BuddyLiveFeedItem> = emptyList(),
  val unviewedAdverseCount: Int = 0,
  val latestAdverseItem: BuddyLiveFeedItem? = null,
  val latestUnviewedAdverseItem: BuddyLiveFeedItem? = null,
)

internal data class BuddyLiveFeedItem(
  val id: Long,
  val timelineItem: BuddyTimelineItem,
  val category: Category,
  val severity: Severity = Severity.LOW,
  val adverse: Boolean = false,
  val viewed: Boolean = false,
  val dismissed: Boolean = false,
  val visibleScreens: List<String> = emptyList(),
) {
  enum class Category(val label: String) {
    SCREEN("Screen"),
    STEP("Step"),
    ERROR("Error"),
    FAILED_HTTP("Failed HTTP"),
    SLOW_SPAN("Slow span"),
    FAILED_SPAN("Failed span"),
  }

  val timestamp: Date
    get() = timelineItem.timestamp
}
