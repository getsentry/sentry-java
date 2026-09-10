package io.sentry.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.cumulative.CumulativeCounter;
import org.jetbrains.annotations.NotNull;

final class SentryCounter extends CumulativeCounter {
  private final @NotNull SentryMeterRegistry registry;
  private final @NotNull SentryMetricInfo metricInfo;

  SentryCounter(
      final @NotNull Meter.Id id,
      final @NotNull SentryMeterRegistry registry,
      final @NotNull SentryMetricInfo metricInfo) {
    super(id);
    this.registry = registry;
    this.metricInfo = metricInfo;
  }

  @Override
  public void increment(final double amount) {
    super.increment(amount);
    if (amount > 0.0 && Double.isFinite(amount)) {
      registry.captureCounter(metricInfo, amount);
    }
  }
}
