package io.sentry.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import org.jetbrains.annotations.NotNull;

final class SentryFunctionTimer<T> extends CumulativeFunctionTimer<T> {
  private final @NotNull SentryMeterRegistry registry;
  private final @NotNull SentryMetricInfo countMetricInfo;
  private final @NotNull SentryMetricInfo totalTimeMetricInfo;
  private volatile boolean removed;
  private boolean countInitialized;
  private double previousCount;
  private boolean totalTimeInitialized;
  private double previousTotalTime;

  SentryFunctionTimer(
      final @NotNull Meter.Id id,
      final @NotNull T obj,
      final @NotNull ToLongFunction<T> countFunction,
      final @NotNull ToDoubleFunction<T> totalTimeFunction,
      final @NotNull TimeUnit totalTimeFunctionUnit,
      final @NotNull TimeUnit baseTimeUnit,
      final @NotNull SentryMeterRegistry registry,
      final @NotNull SentryMetricInfo countMetricInfo,
      final @NotNull SentryMetricInfo totalTimeMetricInfo) {
    super(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, baseTimeUnit);
    this.registry = registry;
    this.countMetricInfo = countMetricInfo;
    this.totalTimeMetricInfo = totalTimeMetricInfo;
  }

  void poll() {
    pollCount();
    pollTotalTime();
  }

  private void pollCount() {
    final double currentCount = count();
    if (!Double.isFinite(currentCount) || removed || registry.isClosed()) {
      return;
    }

    if (!countInitialized || currentCount < previousCount) {
      countInitialized = true;
      previousCount = currentCount;
      return;
    }

    final double delta = currentCount - previousCount;
    previousCount = currentCount;
    if (delta > 0.0 && !removed) {
      registry.captureCounter(countMetricInfo, delta);
    }
  }

  private void pollTotalTime() {
    final double currentTotalTime = totalTime(TimeUnit.MILLISECONDS);
    if (!Double.isFinite(currentTotalTime) || removed || registry.isClosed()) {
      return;
    }

    if (!totalTimeInitialized || currentTotalTime < previousTotalTime) {
      totalTimeInitialized = true;
      previousTotalTime = currentTotalTime;
      return;
    }

    final double delta = currentTotalTime - previousTotalTime;
    previousTotalTime = currentTotalTime;
    if (delta > 0.0 && !removed) {
      registry.captureCounter(totalTimeMetricInfo, delta);
    }
  }

  void markRemoved() {
    removed = true;
  }
}
