package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import io.sentry.android.buddy.model.PerformanceCharacteristics
import io.sentry.android.buddy.ui.common.toSpanDurationStats
import kotlin.test.Test

class SpanDurationStatsTest {
  @Test
  fun `maps a full payload onto the chart stats`() {
    val stats =
      PerformanceCharacteristics(
          spanOp = "db.sql.query",
          duration = 820.5,
          avg = 230.0,
          p50 = 180.0,
          p75 = 280.0,
          p90 = 420.0,
          p95 = 520.0,
        )
        .toSpanDurationStats()

    assertThat(stats).isNotNull()
    assertThat(stats!!.sample).isEqualTo(820.5f)
    assertThat(stats.p95).isEqualTo(520f)
  }

  @Test
  fun `gives no stats when a percentile is missing`() {
    val stats =
      PerformanceCharacteristics(duration = 820.0, avg = 230.0, p50 = 180.0).toSpanDurationStats()

    assertThat(stats).isNull()
  }
}
