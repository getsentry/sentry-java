package io.sentry.android.core.internal.measurement.battery;

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
public final class BatteryLevelMeasurementCollectorFactory implements MeasurementCollectorFactory {

  private final Context applicationContext;

  public BatteryLevelMeasurementCollectorFactory(@NotNull Context applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public @NotNull MeasurementCollector create(
      @NotNull SentryOptions options, @NotNull MeasurementBackgroundService backgroundService) {
    return new BatteryLevelMeasurementCollector(options, backgroundService);
  }

  @Override
  public @Nullable MeasurementBackgroundCollector createBackgroundCollector(
      @NotNull SentryOptions options) {
    return new BatteryLevelBackgroundMeasurementCollector(options, applicationContext);
  }
}
