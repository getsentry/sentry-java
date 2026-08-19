package io.sentry.android.buddy

import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddyLiveFeedItem
import io.sentry.android.buddy.model.BuddyTimelineItem
import io.sentry.android.buddy.model.Severity

internal class BuddyLiveFeedBuffer(private val capacity: Int) {
  private val items = ArrayDeque<BuddyLiveFeedItem>()
  private var nextId = 0L

  fun add(
    item: BuddyTimelineItem,
    category: BuddyLiveFeedItem.Category,
    severity: Severity,
    adverse: Boolean,
    visibleScreens: List<String>,
  ): BuddyLiveFeed {
    nextId++
    items +=
      BuddyLiveFeedItem(
        id = nextId,
        timelineItem = item,
        category = category,
        severity = severity,
        adverse = adverse,
        visibleScreens = visibleScreens,
      )
    while (items.size > capacity) {
      items.removeFirst()
    }
    return snapshot()
  }

  fun markAdverseViewed(): BuddyLiveFeed {
    for (index in items.indices) {
      val item = items[index]
      if (item.adverse) {
        items[index] = item.copy(viewed = true)
      }
    }
    return snapshot()
  }

  fun snapshot(): BuddyLiveFeed {
    val newestFirst = items.reversed()
    val adverseItems = newestFirst.filter { it.adverse }
    val unviewedAdverseItems = adverseItems.filterNot { it.viewed }
    return BuddyLiveFeed(
      items = newestFirst,
      unviewedAdverseCount = unviewedAdverseItems.size,
      latestAdverseItem = adverseItems.maxWithOrNull(BuddyLiveFeedItemAttentionComparator),
      latestUnviewedAdverseItem =
        unviewedAdverseItems.maxWithOrNull(BuddyLiveFeedItemAttentionComparator),
    )
  }

  private companion object {
    private val BuddyLiveFeedItemAttentionComparator =
      compareBy<BuddyLiveFeedItem>({ it.severity.rank }, { it.timestamp.time })

    private val Severity.rank: Int
      get() =
        when (this) {
          Severity.HIGH -> 3
          Severity.MEDIUM -> 2
          Severity.LOW -> 1
        }
  }
}
