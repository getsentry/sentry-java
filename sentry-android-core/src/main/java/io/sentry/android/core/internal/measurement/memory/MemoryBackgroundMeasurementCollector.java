package io.sentry.android.core.internal.measurement.memory;

import static android.content.Context.ACTIVITY_SERVICE;

import android.app.ActivityManager;
import android.content.Context;
import io.sentry.measurement.MeasurementBackgroundCollector;
import io.sentry.measurement.MeasurementBackgroundServiceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MemoryBackgroundMeasurementCollector implements MeasurementBackgroundCollector {

  private final Context applicationContext;

  public MemoryBackgroundMeasurementCollector(@NotNull Context applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public @NotNull MeasurementBackgroundServiceType getMeasurementType() {
    return MeasurementBackgroundServiceType.MEMORY;
  }

  @Override
  public @Nullable Object collect() {
    @Nullable
    ActivityManager am = (ActivityManager) applicationContext.getSystemService(ACTIVITY_SERVICE);
    if (am != null) {
      ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
      am.getMemoryInfo(memInfo);
      return memInfo;
    }
    return null;
  }
}
