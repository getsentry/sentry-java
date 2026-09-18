package io.sentry.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

final class SentryTimer extends CumulativeTimer implements SentryRemovableMeter {
  private final @NotNull SentryMeterRegistry registry;
  private final @NotNull SentryMetricInfo metricInfo;
  private volatile boolean removed;

  SentryTimer(
      final @NotNull Meter.Id id,
      final @NotNull Clock clock,
      final @NotNull DistributionStatisticConfig distributionStatisticConfig,
      final @NotNull PauseDetector pauseDetector,
      final @NotNull TimeUnit baseTimeUnit,
      final @NotNull SentryMeterRegistry registry,
      final @NotNull SentryMetricInfo metricInfo) {
    super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, false);
    this.registry = registry;
    this.metricInfo = metricInfo;
  }

  @Override
  protected void recordNonNegative(final long amount, final @NotNull TimeUnit unit) {
    super.recordNonNegative(amount, unit);
    if (!removed) {
      registry.captureDistribution(metricInfo, toMilliseconds(amount, unit));
    }
  }

  @Override
  public void markRemoved() {
    removed = true;
  }

  private static double toMilliseconds(final long amount, final @NotNull TimeUnit unit) {
    switch (unit) {
      case NANOSECONDS:
        return amount / 1_000_000.0;
      case MICROSECONDS:
        return amount / 1_000.0;
      case MILLISECONDS:
        return amount;
      case SECONDS:
        return amount * 1_000.0;
      case MINUTES:
        return amount * 60_000.0;
      case HOURS:
        return amount * 3_600_000.0;
      case DAYS:
        return amount * 86_400_000.0;
      default:
        throw new IllegalArgumentException("Unsupported time unit: " + unit);
    }
  }
}
