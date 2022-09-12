package io.sentry.android.core.internal.measurement.battery;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.measurement.MeasurementBackgroundCollector;
import io.sentry.measurement.MeasurementBackgroundServiceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BatteryLevelBackgroundMeasurementCollector
    implements MeasurementBackgroundCollector {

  private final @NotNull SentryOptions options;
  private final @NotNull Context applicationContext;

  public BatteryLevelBackgroundMeasurementCollector(
      @NotNull SentryOptions options, @NotNull Context applicationContext) {
    this.options = options;
    this.applicationContext = applicationContext;
  }

  @Override
  public @NotNull MeasurementBackgroundServiceType getMeasurementType() {
    return MeasurementBackgroundServiceType.BATTERY;
  }

  @Override
  public @Nullable Object collect() {
    return getBatteryLevel();
  }

  private @Nullable Float getBatteryLevel() {
    @Nullable Intent batteryIntent = getBatteryIntent();
    if (batteryIntent != null) {
      return getBatteryLevel(batteryIntent);
    }
    return null;
  }

  private @Nullable Intent getBatteryIntent() {
    return applicationContext.registerReceiver(
        null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  }

  private @Nullable Float getBatteryLevel(final @NotNull Intent batteryIntent) {
    try {
      int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
      int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

      if (level == -1 || scale == -1) {
        return null;
      }

      float percentMultiplier = 100.0f;

      return ((float) level / (float) scale) * percentMultiplier;
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting device battery level.", e);
      return null;
    }
  }
}
