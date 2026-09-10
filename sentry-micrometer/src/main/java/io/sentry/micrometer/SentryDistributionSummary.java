package io.sentry.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.cumulative.CumulativeDistributionSummary;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.jetbrains.annotations.NotNull;

final class SentryDistributionSummary extends CumulativeDistributionSummary {
  private final @NotNull SentryMeterRegistry registry;
  private final @NotNull SentryMetricInfo metricInfo;

  SentryDistributionSummary(
      final @NotNull Meter.Id id,
      final @NotNull Clock clock,
      final @NotNull DistributionStatisticConfig distributionStatisticConfig,
      final double scale,
      final @NotNull SentryMeterRegistry registry,
      final @NotNull SentryMetricInfo metricInfo) {
    super(id, clock, distributionStatisticConfig, scale, false);
    this.registry = registry;
    this.metricInfo = metricInfo;
  }

  @Override
  protected void recordNonNegative(final double amount) {
    super.recordNonNegative(amount);
    if (Double.isFinite(amount)) {
      registry.captureDistribution(metricInfo, amount);
    }
  }
}
