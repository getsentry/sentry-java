package io.sentry.util

import com.google.common.truth.Truth.assertThat
import io.sentry.FilterString
import kotlin.test.Test

class MetricsUtilsTest {
  @Test
  fun `no filters or no name does not ignore metrics`() {
    assertThat(MetricsUtils.isIgnored(null, "logback.events")).isFalse()
    assertThat(MetricsUtils.isIgnored(emptyList(), "logback.events")).isFalse()
    assertThat(MetricsUtils.isIgnored(listOf(FilterString(".*")), null)).isFalse()
  }

  @Test
  fun `exact matches are case insensitive`() {
    assertThat(MetricsUtils.isIgnored(listOf(FilterString("LOGBACK.EVENTS")), "logback.events"))
      .isTrue()
  }

  @Test
  fun `regex matches the full name and respects regex case sensitivity`() {
    val filters = listOf(FilterString("logback[.].*"))
    assertThat(MetricsUtils.isIgnored(filters, "logback.events")).isTrue()
    assertThat(MetricsUtils.isIgnored(filters, "prefix.logback.events")).isFalse()
    assertThat(MetricsUtils.isIgnored(filters, "LOGBACK.events")).isFalse()
    assertThat(MetricsUtils.isIgnored(listOf(FilterString("(?i)logback[.].*")), "LOGBACK.events"))
      .isTrue()
  }

  @Test
  fun `invalid regex still supports exact matches without throwing`() {
    val filters = listOf(FilterString("metric["))
    assertThat(MetricsUtils.isIgnored(filters, "METRIC[")).isTrue()
    assertThat(MetricsUtils.isIgnored(filters, "other")).isFalse()
  }

  @Test
  fun `decisions are not shared between option sets or stale after changes`() {
    val filters = mutableListOf(FilterString("ignored"))
    assertThat(MetricsUtils.isIgnored(filters, "ignored")).isTrue()
    assertThat(MetricsUtils.isIgnored(listOf(FilterString("other")), "ignored")).isFalse()
    filters.clear()
    filters.add(FilterString("allowed"))
    assertThat(MetricsUtils.isIgnored(filters, "ignored")).isFalse()
    assertThat(MetricsUtils.isIgnored(filters, "allowed")).isTrue()
  }
}
