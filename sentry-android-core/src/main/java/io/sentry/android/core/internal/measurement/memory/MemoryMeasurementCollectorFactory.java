package io.sentry.android.core.internal.measurement.memory;

import android.content.Context;
import io.sentry.SentryOptions;
import io.sentry.measurement.MeasurementBackgroundCollector;
import io.sentry.measurement.MeasurementBackgroundService;
import io.sentry.measurement.MeasurementCollector;
import io.sentry.measurement.MeasurementCollectorFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MemoryMeasurementCollectorFactory implements MeasurementCollectorFactory {

  private final @NotNull Context applicationContext;

  public MemoryMeasurementCollectorFactory(@NotNull Context applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public @NotNull MeasurementCollector create(
      @NotNull SentryOptions options, @NotNull MeasurementBackgroundService backgroundService) {
    return new MemoryMeasurementCollector(options, applicationContext, backgroundService);
  }

  @Override
  public @Nullable MeasurementBackgroundCollector createBackgroundCollector(
      @NotNull SentryOptions options) {
    return new MemoryBackgroundMeasurementCollector(applicationContext, options);
  }
}
