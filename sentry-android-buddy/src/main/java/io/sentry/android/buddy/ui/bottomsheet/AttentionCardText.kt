package io.sentry.android.buddy.ui.bottomsheet

import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddyLiveFeedItem
import io.sentry.android.buddy.ui.common.formatDurationValue
import io.sentry.android.buddy.ui.common.humanizeDotKey
import io.sentry.android.buddy.ui.common.longValue
import io.sentry.android.buddy.ui.common.mapValue
import io.sentry.android.buddy.ui.common.stringValue
import io.sentry.android.buddy.ui.common.timeline.BuddyTimelineRow
import io.sentry.android.buddy.ui.common.timeline.toTimelineRow

internal fun BuddyLiveFeedItem.usesRichAttentionLayout(): Boolean =
  category == BuddyLiveFeedItem.Category.ERROR ||
    category == BuddyLiveFeedItem.Category.SLOW_SPAN ||
    category == BuddyLiveFeedItem.Category.FAILED_SPAN ||
    category == BuddyLiveFeedItem.Category.FAILED_HTTP

internal fun BuddyLiveFeedItem.performanceHeadline(): String =
  when (category) {
    BuddyLiveFeedItem.Category.ERROR -> "Unhandled error captured"
    BuddyLiveFeedItem.Category.SLOW_SPAN -> "Performance issue detected"
    BuddyLiveFeedItem.Category.FAILED_SPAN -> "Instrumented work failed"
    BuddyLiveFeedItem.Category.FAILED_HTTP -> "Request returned an error"
    else -> "Needs attention"
  }

internal fun BuddyLiveFeedItem.performanceSourceLabel(): String? =
  when (category) {
    BuddyLiveFeedItem.Category.ERROR -> "Exception"
    BuddyLiveFeedItem.Category.FAILED_HTTP -> "HTTP"
    BuddyLiveFeedItem.Category.SLOW_SPAN,
    BuddyLiveFeedItem.Category.FAILED_SPAN -> timelineItem.data.stringValue("op")?.humanizeDotKey()

    else -> null
  }

internal fun BuddyLiveFeedItem.attentionPrimaryStat(liveFeed: BuddyLiveFeed): PerformanceStat? {
  val duration = timelineItem.data.longValue("duration_ms")
  if (duration != null) {
    return PerformanceStat(formatDurationValue(duration), "Duration")
  }
  val statusCode =
    timelineItem.data.mapValue("data").longValue("status_code")
      ?: timelineItem.data.longValue("status_code")
  if (statusCode != null) {
    return PerformanceStat(statusCode.toString(), "Status")
  }
  if (category == BuddyLiveFeedItem.Category.ERROR) {
    val errorCount =
      liveFeed.items
        .count { it.adverse && it.category == BuddyLiveFeedItem.Category.ERROR }
        .coerceAtLeast(1)
    val label = if (errorCount == 1) "Error" else "Errors"
    return PerformanceStat(errorCount.toString(), label)
  }
  return null
}

internal fun BuddyLiveFeedItem.performanceNarrative(liveFeed: BuddyLiveFeed): String {
  val scope = screenContextText() ?: "Buddy is tracking the surrounding user flow."
  val supportingStats =
    listOfNotNull(
        liveFeed.items
          .count { it.adverse && it.category == BuddyLiveFeedItem.Category.SLOW_SPAN }
          .positiveChip("slow spans"),
        liveFeed.items
          .count { it.adverse && it.category == BuddyLiveFeedItem.Category.FAILED_SPAN }
          .positiveChip("failed spans"),
        liveFeed.items
          .count { it.adverse && it.category == BuddyLiveFeedItem.Category.FAILED_HTTP }
          .positiveChip("HTTP issues"),
      )
      .joinToString()
  return if (supportingStats.isBlank()) {
    scope
  } else {
    "$scope Recent pattern: $supportingStats."
  }
}

internal fun BuddyLiveFeedItem.screenContextText(): String? {
  if (visibleScreens.isEmpty()) {
    return null
  }
  val label = if (visibleScreens.size == 1) "Screen" else "Screens"
  return "$label: ${visibleScreens.joinToString(" -> ")}"
}

/**
 * The attention card shows the picked item with a few of its neighbours, so the reader can see what
 * led up to it.
 */
internal fun attentionTimelineRows(
  item: BuddyLiveFeedItem,
  items: List<BuddyLiveFeedItem>,
  nowMs: Long,
  radius: Int = ATTENTION_TIMELINE_RADIUS,
): List<BuddyTimelineRow> {
  val index = items.indexOfFirst { it.id == item.id }
  if (index < 0) {
    return listOf(item.toTimelineRow(nowMs, emphasized = true))
  }
  val from = (index - radius).coerceAtLeast(0)
  val to = (index + radius).coerceAtMost(items.lastIndex)
  return items.subList(from, to + 1).map {
    it.toTimelineRow(nowMs, emphasized = it.id == item.id)
  }
}

private const val ATTENTION_TIMELINE_RADIUS = 2
