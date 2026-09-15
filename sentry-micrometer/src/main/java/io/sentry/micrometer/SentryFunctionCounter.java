package io.sentry.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import java.util.function.ToDoubleFunction;
import org.jetbrains.annotations.NotNull;

final class SentryFunctionCounter<T> extends CumulativeFunctionCounter<T> {
  private final @NotNull SentryMeterRegistry registry;
  private final @NotNull SentryMetricInfo metricInfo;
  private volatile boolean removed;
  private boolean initialized;
  private double previousValue;

  SentryFunctionCounter(
      final @NotNull Meter.Id id,
      final @NotNull T obj,
      final @NotNull ToDoubleFunction<T> countFunction,
      final @NotNull SentryMeterRegistry registry,
      final @NotNull SentryMetricInfo metricInfo) {
    super(id, obj, countFunction);
    this.registry = registry;
    this.metricInfo = metricInfo;
  }

  void poll() {
    final double currentValue = count();
    if (!Double.isFinite(currentValue) || removed || registry.isClosed()) {
      return;
    }

    if (!initialized || currentValue < previousValue) {
      initialized = true;
      previousValue = currentValue;
      return;
    }

    final double delta = currentValue - previousValue;
    previousValue = currentValue;
    if (delta > 0.0 && !removed) {
      registry.captureCounter(metricInfo, delta);
    }
  }

  void markRemoved() {
    removed = true;
  }
}
