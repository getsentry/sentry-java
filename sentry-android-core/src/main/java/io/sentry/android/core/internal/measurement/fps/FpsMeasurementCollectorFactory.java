package io.sentry.android.core.internal.measurement.fps;

import io.sentry.SentryOptions;
import io.sentry.measurement.MeasurementBackgroundService;
import io.sentry.measurement.MeasurementCollector;
import io.sentry.measurement.MeasurementCollectorFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class FpsMeasurementCollectorFactory implements MeasurementCollectorFactory {

  @Override
  public @NotNull MeasurementCollector create(
      @NotNull SentryOptions options, @NotNull MeasurementBackgroundService backgroundService) {
    return new FpsMeasurementCollector();
  }
}
