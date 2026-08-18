package io.sentry.android.buddy

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

internal class BuddyLiveFeedBuffer(private val capacity: Int) {
  private val items = ArrayDeque<BuddyLiveFeedItem>()
  private var nextId = 0L

  fun add(
    item: BuddyTimelineItem,
    category: BuddyLiveFeedItem.Category,
    severity: Severity,
    adverse: Boolean,
  ): BuddyLiveFeed {
    nextId++
    items +=
      BuddyLiveFeedItem(
        id = nextId,
        timelineItem = item,
        category = category,
        severity = severity,
        adverse = adverse,
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
