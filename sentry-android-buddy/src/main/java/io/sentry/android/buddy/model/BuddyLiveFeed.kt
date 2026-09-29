package io.sentry.android.buddy.model

import java.util.Date

internal data class BuddyLiveFeed(
  val items: List<BuddyLiveFeedItem> = emptyList(),
  val unviewedAdverseCount: Int = 0,
  val latestAdverseItem: BuddyLiveFeedItem? = null,
  val latestUnviewedAdverseItem: BuddyLiveFeedItem? = null,
)

/** The same feed with every adverse item marked as viewed, so nothing is pending any more. */
internal fun BuddyLiveFeed.markAdverseViewed(): BuddyLiveFeed =
  copy(
    items = items.map { if (it.adverse) it.copy(viewed = true) else it },
    unviewedAdverseCount = 0,
    latestAdverseItem = latestAdverseItem?.copy(viewed = true),
    latestUnviewedAdverseItem = null,
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
  /** The captured exception behind an [Category.ERROR] item, shown inline in the live feed. */
  val exception: BuddyExceptionReport? = null,
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
