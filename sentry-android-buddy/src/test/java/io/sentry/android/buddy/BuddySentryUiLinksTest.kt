package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import java.util.Date
import kotlin.test.Test

class BuddySentryUiLinksTest {
  @Test
  fun `error item links to event id search`() {
    val links =
      BuddySentryUiLinks(baseUrl = "https://sentry-sdks.sentry.io/", projectId = "5428559")
    val item = liveFeedItem(BuddyLiveFeedItem.Category.ERROR, mapOf("event_id" to "abc123"))

    assertThat(links.linkFor(item))
      .isEqualTo("https://sentry-sdks.sentry.io/issues/?project=5428559&query=id%3Aabc123")
  }

  @Test
  fun `span item links to trace view`() {
    val links = BuddySentryUiLinks(baseUrl = "https://sentry-sdks.sentry.io", projectId = "5428559")
    val item =
      liveFeedItem(
        BuddyLiveFeedItem.Category.SLOW_SPAN,
        mapOf("trace_id" to "trace-id", "span_id" to "span-id"),
      )

    assertThat(links.linkFor(item))
      .isEqualTo(
        "https://sentry-sdks.sentry.io/performance/trace/trace-id/?project=5428559&span=span-id"
      )
  }

  @Test
  fun `organization slug provides default base url`() {
    val links = BuddySentryUiLinks(organizationSlug = "sentry-sdks", projectId = "5428559")
    val item = liveFeedItem(BuddyLiveFeedItem.Category.ERROR, mapOf("event_id" to "abc123"))

    assertThat(links.linkFor(item))
      .isEqualTo("https://sentry-sdks.sentry.io/issues/?project=5428559&query=id%3Aabc123")
  }

  @Test
  fun `missing config returns no link`() {
    val links = BuddySentryUiLinks(baseUrl = "https://sentry-sdks.sentry.io")
    val item = liveFeedItem(BuddyLiveFeedItem.Category.ERROR, mapOf("event_id" to "abc123"))

    assertThat(links.linkFor(item)).isNull()
  }

  @Test
  fun `screen item returns no link`() {
    val links = BuddySentryUiLinks(baseUrl = "https://sentry-sdks.sentry.io", projectId = "5428559")
    val item = liveFeedItem(BuddyLiveFeedItem.Category.SCREEN, emptyMap())

    assertThat(links.linkFor(item)).isNull()
  }

  private fun liveFeedItem(
    category: BuddyLiveFeedItem.Category,
    data: Map<String, Any?>,
  ): BuddyLiveFeedItem =
    BuddyLiveFeedItem(
      id = 1,
      timelineItem = BuddyTimelineItem(BuddyTimelineItem.Type.EVENT, Date(0), 0, data = data),
      category = category,
    )
}
