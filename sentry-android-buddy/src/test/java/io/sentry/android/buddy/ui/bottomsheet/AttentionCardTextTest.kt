package io.sentry.android.buddy.ui.bottomsheet

import com.google.common.truth.Truth.assertThat
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddyLiveFeedItem
import io.sentry.android.buddy.model.BuddyTimelineItem
import io.sentry.android.buddy.model.Severity
import java.util.Date
import kotlin.test.Test

class AttentionCardTextTest {
  @Test
  fun `regular errors use rich attention layout`() {
    val error = item(BuddyLiveFeedItem.Category.ERROR, "HttpException", Severity.HIGH)

    assertThat(error.usesRichAttentionLayout()).isTrue()
    assertThat(error.performanceHeadline()).isEqualTo("Unhandled error captured")
    assertThat(error.performanceSourceLabel()).isEqualTo("Exception")
  }

  @Test
  fun `regular error primary stat shows recent error count`() {
    val error = item(BuddyLiveFeedItem.Category.ERROR, "HttpException", Severity.HIGH)
    val feed =
      BuddyLiveFeed(
        items =
          listOf(
            error,
            item(BuddyLiveFeedItem.Category.ERROR, "SentryHttpClientException", Severity.HIGH),
            item(BuddyLiveFeedItem.Category.FAILED_HTTP, "GET /repos", Severity.MEDIUM),
          )
      )

    assertThat(error.attentionPrimaryStat(feed)).isEqualTo(PerformanceStat("2", "Errors"))
  }

  @Test
  fun `failed HTTP primary stat still prefers status code`() {
    val failedHttp =
      item(
        BuddyLiveFeedItem.Category.FAILED_HTTP,
        "GET https://demo.sentry.dev/travel/availability",
        Severity.HIGH,
        data = mapOf("data" to mapOf("status_code" to 503L)),
      )

    assertThat(failedHttp.attentionPrimaryStat(BuddyLiveFeed(items = listOf(failedHttp))))
      .isEqualTo(PerformanceStat("503", "Status"))
  }

  @Test
  fun `attention card keeps showing latest adverse item after it is viewed`() {
    val error = item(BuddyLiveFeedItem.Category.ERROR, "HttpException", Severity.HIGH)
    val feed = BuddyLiveFeed(latestAdverseItem = error, latestUnviewedAdverseItem = null)

    assertThat(feed.attentionCardItem()).isEqualTo(error)
  }

  private fun item(
    category: BuddyLiveFeedItem.Category,
    name: String,
    severity: Severity = Severity.MEDIUM,
    data: Map<String, Any?> = emptyMap(),
  ): BuddyLiveFeedItem =
    BuddyLiveFeedItem(
      id = name.hashCode().toLong(),
      timelineItem =
        BuddyTimelineItem(
          type = BuddyTimelineItem.Type.EVENT,
          timestamp = Date(1_700_000_000_000L),
          elapsedMs = 1_000L,
          name = name,
          data = data,
        ),
      category = category,
      severity = severity,
      adverse = true,
    )
}
