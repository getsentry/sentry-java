package io.sentry.android.core.internal.measurement.cpu;

import io.sentry.SentryOptions;
import io.sentry.measurement.MeasurementBackgroundCollector;
import io.sentry.measurement.MeasurementBackgroundService;
import io.sentry.measurement.MeasurementCollector;
import io.sentry.measurement.MeasurementCollectorFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class CpuMeasurementCollectorFactory implements MeasurementCollectorFactory {
  @Override
  public @NotNull MeasurementCollector create(
      @NotNull SentryOptions options, @NotNull MeasurementBackgroundService backgroundService) {
    return new CpuMeasurementCollector(options, backgroundService);
  }

  @Override
  public @Nullable MeasurementBackgroundCollector createBackgroundCollector(
      @NotNull SentryOptions options) {
    return new CpuBackgroundMeasurementCollector(options);
  }
}
