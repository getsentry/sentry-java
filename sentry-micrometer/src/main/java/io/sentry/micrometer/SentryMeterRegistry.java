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
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.sentry.Sentry;
import io.sentry.SentryAttributes;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.metrics.MetricsUnit;
import io.sentry.util.ExceptionUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A Micrometer registry that forwards metrics to Sentry. */
public final class SentryMeterRegistry extends MeterRegistry {
  private static final @NotNull String INTEGRATION_NAME = "Micrometer";
  private static final long DEFAULT_POLL_INTERVAL_MILLIS = 60_000;

  private final @Nullable ScheduledExecutorService scheduler;
  private final @Nullable ScheduledFuture<?> pollingTask;

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-micrometer", BuildConfig.VERSION_NAME);
  }

  /** Creates a registry that polls passive meters every 60 seconds. */
  public SentryMeterRegistry() {
    this(DEFAULT_POLL_INTERVAL_MILLIS);
  }

  /**
   * Creates a registry with the given passive meter polling interval in milliseconds.
   *
   * <p>A zero interval disables passive meter polling. Negative intervals are not supported.
   */
  public SentryMeterRegistry(final long pollIntervalMillis) {
    this(pollIntervalMillis, Clock.SYSTEM, createScheduler(pollIntervalMillis));
  }

  SentryMeterRegistry(
      final long pollIntervalMillis,
      final @NotNull Clock clock,
      final @Nullable ScheduledExecutorService scheduler) {
    super(clock);
    validatePollInterval(pollIntervalMillis);
    if (pollIntervalMillis > 0 && scheduler == null) {
      throw new IllegalArgumentException(
          "A scheduler is required when passive polling is enabled.");
    }
    this.scheduler = pollIntervalMillis == 0 ? null : scheduler;
    config().namingConvention(NamingConvention.dot).onMeterRemoved(this::onMeterRemoved);
    addIntegrationToSdkVersion(INTEGRATION_NAME);
    if (this.scheduler == null) {
      pollingTask = null;
    } else {
      pollingTask =
          this.scheduler.scheduleAtFixedRate(
              this::pollMeters, pollIntervalMillis, pollIntervalMillis, TimeUnit.MILLISECONDS);
    }
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
    return new SentryFunctionCounter<>(id, obj, countFunction, this, createMetricInfo(id));
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

  void captureGauge(final @NotNull SentryMetricInfo metricInfo, final double value) {
    if (isClosed()) {
      return;
    }
    Sentry.getCurrentScopes()
        .metrics()
        .gauge(metricInfo.getName(), value, metricInfo.getUnit(), metricInfo.createParameters());
  }

  void pollMeters() {
    if (isClosed()) {
      return;
    }
    for (final @NotNull Meter meter : getMeters()) {
      if (isClosed()) {
        return;
      }
      try {
        publishPassiveMeter(meter);
      } catch (Throwable throwable) {
        ExceptionUtils.rethrowIfFatal(throwable);
        Sentry.getCurrentScopes()
            .getOptions()
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                throwable,
                "Failed to publish Micrometer meter %s to Sentry.",
                meter.getId().getName());
      }
    }
  }

  private void publishPassiveMeter(final @NotNull Meter meter) {
    if (meter instanceof TimeGauge) {
      publishTimeGauge((TimeGauge) meter);
    } else if (meter instanceof Gauge) {
      publishGauge((Gauge) meter);
    } else if (meter instanceof LongTaskTimer) {
      publishLongTaskTimer((LongTaskTimer) meter);
    } else if (meter instanceof SentryFunctionCounter) {
      ((SentryFunctionCounter<?>) meter).poll();
    }
  }

  private void publishGauge(final @NotNull Gauge gauge) {
    final double value = gauge.value();
    if (Double.isFinite(value)) {
      captureGauge(createMetricInfo(gauge.getId()), value);
    }
  }

  private void publishTimeGauge(final @NotNull TimeGauge gauge) {
    final double value = gauge.value(TimeUnit.MILLISECONDS);
    if (Double.isFinite(value)) {
      captureGauge(createMetricInfo(gauge.getId(), MetricsUnit.Duration.MILLISECOND), value);
    }
  }

  private void publishLongTaskTimer(final @NotNull LongTaskTimer timer) {
    final @NotNull SentryMetricInfo activeMetric = createMetricInfo(timer.getId(), ".active", null);
    captureGauge(activeMetric, timer.activeTasks());

    final double duration = timer.duration(TimeUnit.MILLISECONDS);
    if (Double.isFinite(duration)) {
      captureGauge(
          createMetricInfo(timer.getId(), ".duration", MetricsUnit.Duration.MILLISECOND), duration);
    }
  }

  private void onMeterRemoved(final @NotNull Meter meter) {
    if (meter instanceof SentryFunctionCounter) {
      ((SentryFunctionCounter<?>) meter).markRemoved();
    }
  }

  private @NotNull SentryMetricInfo createMetricInfo(final @NotNull Meter.Id id) {
    return createMetricInfo(id, SentryMetricUnit.normalize(id.getBaseUnit()));
  }

  private @NotNull SentryMetricInfo createMetricInfo(
      final @NotNull Meter.Id id, final @Nullable String unit) {
    return createMetricInfo(id, "", unit);
  }

  private @NotNull SentryMetricInfo createMetricInfo(
      final @NotNull Meter.Id id, final @NotNull String suffix, final @Nullable String unit) {
    final @NotNull Map<String, Object> attributes = new HashMap<>();
    for (final @NotNull Tag tag : getConventionTags(id)) {
      attributes.put(tag.getKey(), tag.getValue());
    }
    return new SentryMetricInfo(
        getConventionName(id) + suffix, unit, SentryAttributes.fromMap(attributes));
  }

  @Override
  public void close() {
    if (isClosed()) {
      return;
    }
    super.close();
    if (pollingTask != null) {
      pollingTask.cancel(true);
    }
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
  }

  private static @Nullable ScheduledExecutorService createScheduler(final long pollIntervalMillis) {
    validatePollInterval(pollIntervalMillis);
    if (pollIntervalMillis == 0) {
      return null;
    }
    return Executors.newSingleThreadScheduledExecutor(
        new NamedThreadFactory("sentry-micrometer-poller"));
  }

  private static void validatePollInterval(final long pollIntervalMillis) {
    if (pollIntervalMillis < 0) {
      throw new IllegalArgumentException("The passive meter polling interval cannot be negative.");
    }
  }
}
