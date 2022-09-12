package io.sentry.android.core.internal.measurement.memory;

import static android.content.Context.ACTIVITY_SERVICE;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import io.sentry.SentryOptions;
import io.sentry.measurement.MeasurementBackgroundCollector;
import io.sentry.measurement.MeasurementBackgroundServiceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MemoryBackgroundMeasurementCollector implements MeasurementBackgroundCollector {

  private final @NotNull Context applicationContext;
  //  private final @NotNull SentryOptions options;

  public MemoryBackgroundMeasurementCollector(
      @NotNull Context applicationContext, @NotNull SentryOptions options) {
    this.applicationContext = applicationContext;
    //    this.options = options;
  }

  @Override
  public @NotNull MeasurementBackgroundServiceType getMeasurementType() {
    return MeasurementBackgroundServiceType.MEMORY;
  }

  @Override
  @SuppressLint("NewApi")
  public @Nullable Object collect() {
    // TODO 0.3 - 5 ms
    @Nullable
    ActivityManager am = (ActivityManager) applicationContext.getSystemService(ACTIVITY_SERVICE);
    if (am != null) {
      // TODO remeasure 0.1 - 10 ms
      ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
      am.getMemoryInfo(memInfo);
      long freeMem = memInfo.totalMem - memInfo.availMem;

      // TODO 1 - 5 μs
      Runtime runtime = Runtime.getRuntime();
      return new MemoryReading(
          freeMem,
          memInfo.totalMem,
          runtime.freeMemory(),
          runtime.totalMemory(),
          runtime.maxMemory());
    }

    return null;
  }

  public static final class MemoryReading {
    private final long am;
    private final long amTotal;
    private final long runtime;
    private final long runtimeTotal;
    private final long runtimeMax;

    public MemoryReading(long am, long amTotal, long runtime, long runtimeTotal, long runtimeMax) {
      this.am = am;
      this.amTotal = amTotal;
      this.runtime = runtime;
      this.runtimeTotal = runtimeTotal;
      this.runtimeMax = runtimeMax;
    }

    public long getAm() {
      return am;
    }

    public long getRuntime() {
      return runtime;
    }

    public long getAmTotal() {
      return amTotal;
    }

    public long getRuntimeTotal() {
      return runtimeTotal;
    }

    public long getRuntimeMax() {
      return runtimeMax;
    }
  }
}
