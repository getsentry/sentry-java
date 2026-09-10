package io.sentry.micrometer

import com.google.common.truth.Truth.assertThat
import io.sentry.metrics.MetricsUnit
import kotlin.test.Test

class SentryMetricUnitTest {
  @Test
  fun `normalizes known aliases`() {
    assertThat(SentryMetricUnit.normalize("ms")).isEqualTo(MetricsUnit.Duration.MILLISECOND)
    assertThat(SentryMetricUnit.normalize("seconds")).isEqualTo(MetricsUnit.Duration.SECOND)
    assertThat(SentryMetricUnit.normalize("bytes")).isEqualTo(MetricsUnit.Information.BYTE)
    assertThat(SentryMetricUnit.normalize("KiB")).isEqualTo(MetricsUnit.Information.KIBIBYTE)
    assertThat(SentryMetricUnit.normalize("percentage")).isEqualTo(MetricsUnit.Fraction.PERCENT)
  }

  @Test
  fun `preserves unknown and absent units`() {
    assertThat(SentryMetricUnit.normalize("widgets")).isEqualTo("widgets")
    assertThat(SentryMetricUnit.normalize(null)).isNull()
  }
}
