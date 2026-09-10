package io.sentry.micrometer;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.sentry.Sentry;
import io.sentry.SentryAttributes;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.metrics.MetricsUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A Micrometer registry that forwards metrics to Sentry. */
public final class SentryMeterRegistry extends MeterRegistry {
  private static final @NotNull String INTEGRATION_NAME = "Micrometer";

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-micrometer", BuildConfig.VERSION_NAME);
  }

  /** Creates a registry that forwards active meter observations to Sentry. */
  public SentryMeterRegistry() {
    super(Clock.SYSTEM);
    addIntegrationToSdkVersion(INTEGRATION_NAME);
  }

  @Override
  protected @NotNull Counter newCounter(final @NotNull Meter.Id id) {
    return new SentryCounter(id, this, createMetricInfo(id));
  }

  @Override
  protected @NotNull Timer newTimer(
      final @NotNull Meter.Id id,
      final @NotNull DistributionStatisticConfig distributionStatisticConfig,
      final @NotNull PauseDetector pauseDetector) {
    return new SentryTimer(
        id,
        clock,
        distributionStatisticConfig,
        pauseDetector,
        getBaseTimeUnit(),
        this,
        createMetricInfo(id, MetricsUnit.Duration.MILLISECOND));
  }

  @Override
  protected @NotNull DistributionSummary newDistributionSummary(
      final @NotNull Meter.Id id,
      final @NotNull DistributionStatisticConfig distributionStatisticConfig,
      final double scale) {
    return new SentryDistributionSummary(
        id, clock, distributionStatisticConfig, scale, this, createMetricInfo(id));
  }

  @Override
  protected <T> @NotNull Gauge newGauge(
      final @NotNull Meter.Id id,
      final @Nullable T obj,
      final @NotNull ToDoubleFunction<T> valueFunction) {
    return new DefaultGauge<>(id, obj, valueFunction);
  }

  @Override
  protected @NotNull LongTaskTimer newLongTaskTimer(
      final @NotNull Meter.Id id,
      final @NotNull DistributionStatisticConfig distributionStatisticConfig) {
    return new DefaultLongTaskTimer(
        id, clock, getBaseTimeUnit(), distributionStatisticConfig, false);
  }

  @Override
  protected <T> @NotNull FunctionTimer newFunctionTimer(
      final @NotNull Meter.Id id,
      final @NotNull T obj,
      final @NotNull ToLongFunction<T> countFunction,
      final @NotNull ToDoubleFunction<T> totalTimeFunction,
      final @NotNull TimeUnit totalTimeFunctionUnit) {
    return new CumulativeFunctionTimer<>(
        id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
  }

  @Override
  protected <T> @NotNull FunctionCounter newFunctionCounter(
      final @NotNull Meter.Id id,
      final @NotNull T obj,
      final @NotNull ToDoubleFunction<T> countFunction) {
    return new CumulativeFunctionCounter<>(id, obj, countFunction);
  }

  @Override
  protected @NotNull Meter newMeter(
      final @NotNull Meter.Id id,
      final @NotNull Meter.Type type,
      final @NotNull Iterable<Measurement> measurements) {
    Sentry.getCurrentScopes()
        .getOptions()
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "Micrometer meter type %s is not supported for Sentry export.",
            type);
    return new DefaultMeter(id, type, measurements);
  }

  @Override
  protected @NotNull TimeUnit getBaseTimeUnit() {
    return TimeUnit.MILLISECONDS;
  }

  @Override
  protected @NotNull DistributionStatisticConfig defaultHistogramConfig() {
    return DistributionStatisticConfig.DEFAULT;
  }

  void captureCounter(final @NotNull SentryMetricInfo metricInfo, final double value) {
    if (isClosed()) {
      return;
    }
    Sentry.getCurrentScopes()
        .metrics()
        .count(metricInfo.getName(), value, metricInfo.getUnit(), metricInfo.createParameters());
  }

  void captureDistribution(final @NotNull SentryMetricInfo metricInfo, final double value) {
    if (isClosed()) {
      return;
    }
    Sentry.getCurrentScopes()
        .metrics()
        .distribution(
            metricInfo.getName(), value, metricInfo.getUnit(), metricInfo.createParameters());
  }

  private @NotNull SentryMetricInfo createMetricInfo(final @NotNull Meter.Id id) {
    return createMetricInfo(id, SentryMetricUnit.normalize(id.getBaseUnit()));
  }

  private @NotNull SentryMetricInfo createMetricInfo(
      final @NotNull Meter.Id id, final @Nullable String unit) {
    final @NotNull Map<String, Object> attributes = new HashMap<>();
    for (final @NotNull Tag tag : getConventionTags(id)) {
      attributes.put(tag.getKey(), tag.getValue());
    }
    return new SentryMetricInfo(getConventionName(id), unit, SentryAttributes.fromMap(attributes));
  }
}
