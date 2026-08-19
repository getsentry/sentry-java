package io.sentry.android.buddy.ui.common.timeline

import io.sentry.android.buddy.model.BuddyLiveFeedItem
import io.sentry.android.buddy.model.BuddyTimelineItem
import io.sentry.android.buddy.model.Severity
import io.sentry.android.buddy.ui.common.formatDurationValue
import io.sentry.android.buddy.ui.common.formatElapsed
import io.sentry.android.buddy.ui.common.longValue
import io.sentry.android.buddy.ui.common.relativeTime
import io.sentry.android.buddy.ui.common.stringValue

/** Live feed rows are stamped with their age, because there is no recording to count from. */
internal fun BuddyLiveFeedItem.toTimelineRow(
  nowMs: Long,
  link: String? = null,
  emphasized: Boolean = false,
): BuddyTimelineRow =
  BuddyTimelineRow(
    id = id,
    stamp = relativeTime(timestamp.time, nowMs),
    category = categoryKey(),
    detail = title(),
    trailing = durationText(),
    tone = tone(),
    emphasized = emphasized,
    link = link,
  )

/** Recording rows are stamped with the elapsed time since the recording started. */
internal fun BuddyTimelineItem.toTimelineRow(id: Long): BuddyTimelineRow =
  BuddyTimelineRow(
    id = id,
    stamp = formatElapsed(elapsedMs),
    category = data.stringValue("op") ?: type.value,
    detail = name.orEmpty(),
    trailing = data.longValue("duration_ms")?.let { formatDurationValue(it) },
    tone =
      when (type) {
        BuddyTimelineItem.Type.EVENT -> BuddyTimelineTone.ERROR
        BuddyTimelineItem.Type.STEP -> BuddyTimelineTone.ACCENT
        BuddyTimelineItem.Type.SCREEN -> BuddyTimelineTone.NEUTRAL
        else -> BuddyTimelineTone.NEUTRAL
      },
  )

private fun BuddyLiveFeedItem.categoryKey(): String =
  when (category) {
    BuddyLiveFeedItem.Category.SCREEN -> "navigation"
    BuddyLiveFeedItem.Category.STEP -> "step"
    BuddyLiveFeedItem.Category.ERROR -> "error"
    BuddyLiveFeedItem.Category.FAILED_HTTP -> timelineItem.data.stringValue("op") ?: "http.client"
    BuddyLiveFeedItem.Category.SLOW_SPAN,
    BuddyLiveFeedItem.Category.FAILED_SPAN -> timelineItem.data.stringValue("op") ?: "span"
  }

private fun BuddyLiveFeedItem.durationText(): String? =
  timelineItem.data.longValue("duration_ms")?.let { formatDurationValue(it) }

private fun BuddyLiveFeedItem.tone(): BuddyTimelineTone {
  val byCategory =
    when (category) {
      BuddyLiveFeedItem.Category.ERROR,
      BuddyLiveFeedItem.Category.FAILED_SPAN -> BuddyTimelineTone.ERROR

      BuddyLiveFeedItem.Category.FAILED_HTTP,
      BuddyLiveFeedItem.Category.SLOW_SPAN -> BuddyTimelineTone.WARNING

      BuddyLiveFeedItem.Category.STEP -> BuddyTimelineTone.ACCENT
      BuddyLiveFeedItem.Category.SCREEN -> BuddyTimelineTone.NEUTRAL
    }
  if (!adverse) {
    return byCategory
  }
  return when (severity) {
    Severity.HIGH -> BuddyTimelineTone.ERROR
    Severity.MEDIUM -> BuddyTimelineTone.WARNING
    Severity.LOW -> byCategory
  }
}
