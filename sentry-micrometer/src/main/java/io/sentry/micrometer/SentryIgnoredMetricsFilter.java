package io.sentry.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.sentry.FilterString;
import io.sentry.IScopes;
import io.sentry.util.MetricsUtils;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SentryIgnoredMetricsFilter implements MeterFilter {
  private final @NotNull SentryMeterRegistry registry;
  private final @NotNull IScopes scopes;

  SentryIgnoredMetricsFilter(
      final @NotNull SentryMeterRegistry registry, final @NotNull IScopes scopes) {
    this.registry = registry;
    this.scopes = scopes;
  }

  @Override
  public @NotNull MeterFilterReply accept(final @NotNull Meter.Id id) {
    final @Nullable List<FilterString> ignoredMetrics =
        scopes.getOptions().getMetrics().getIgnoredMetrics();
    if (ignoredMetrics == null || ignoredMetrics.isEmpty()) {
      return MeterFilterReply.NEUTRAL;
    }
    final boolean ignored;
    switch (id.getType()) {
      case LONG_TASK_TIMER:
        final String longTaskName = getTimeMeterName(id);
        ignored =
            MetricsUtils.isIgnored(ignoredMetrics, longTaskName + ".active")
                && MetricsUtils.isIgnored(ignoredMetrics, longTaskName + ".duration");
        break;
      case TIMER:
        // Timer and FunctionTimer share a type. Keep the meter unless all possible outputs
        // are ignored; MetricsApi filters each individual output when it is captured.
        final String timerName = getTimeMeterName(id);
        ignored =
            MetricsUtils.isIgnored(ignoredMetrics, timerName)
                && MetricsUtils.isIgnored(ignoredMetrics, timerName + ".count")
                && MetricsUtils.isIgnored(ignoredMetrics, timerName + ".total_time");
        break;
      case GAUGE:
        // Gauge and TimeGauge also share a type, but only TimeGauge changes the base unit.
        ignored =
            MetricsUtils.isIgnored(
                    ignoredMetrics, id.getConventionName(registry.config().namingConvention()))
                && MetricsUtils.isIgnored(ignoredMetrics, getTimeMeterName(id));
        break;
      default:
        ignored =
            MetricsUtils.isIgnored(
                ignoredMetrics, id.getConventionName(registry.config().namingConvention()));
        break;
    }
    return ignored ? MeterFilterReply.DENY : MeterFilterReply.NEUTRAL;
  }

  private @NotNull String getTimeMeterName(final @NotNull Meter.Id id) {
    // MeterRegistry normalizes time-meter IDs after its filters run, using the default locale.
    return id.withBaseUnit(registry.getBaseTimeUnit().toString().toLowerCase(Locale.getDefault()))
        .getConventionName(registry.config().namingConvention());
  }
}
