package io.sentry.android.buddy.ui.common.timeline

import io.sentry.android.buddy.model.BuddyLiveFeedItem
import io.sentry.android.buddy.ui.common.mapValue
import io.sentry.android.buddy.ui.common.stringValue

internal fun BuddyLiveFeedItem.title(): String =
  when (category) {
    BuddyLiveFeedItem.Category.SCREEN -> timelineItem.name ?: "Unknown screen"
    BuddyLiveFeedItem.Category.STEP -> timelineItem.name ?: "Unnamed step"
    BuddyLiveFeedItem.Category.ERROR -> timelineItem.name ?: "Error captured"
    BuddyLiveFeedItem.Category.FAILED_HTTP -> httpTitle()
    BuddyLiveFeedItem.Category.SLOW_SPAN -> timelineItem.name ?: "Slow span"
    BuddyLiveFeedItem.Category.FAILED_SPAN -> timelineItem.name ?: "Failed span"
  }

internal fun BuddyLiveFeedItem.httpTitle(): String {
  val data = timelineItem.data.mapValue("data")
  val method = data.stringValue("method") ?: data.stringValue("http.method")
  val url = data.stringValue("url") ?: data.stringValue("http.url")
  return listOfNotNull(method, url).joinToString(" ").ifBlank { timelineItem.name ?: "Failed HTTP" }
}
