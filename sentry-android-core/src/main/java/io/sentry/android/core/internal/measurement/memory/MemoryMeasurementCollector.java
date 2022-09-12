package io.sentry.android.core.internal.measurement.memory;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import io.sentry.ITransaction;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.measurement.BackgroundAwareMeasurementCollector;
import io.sentry.measurement.MeasurementBackgroundService;
import io.sentry.measurement.MeasurementBackgroundServiceType;
import io.sentry.measurement.MeasurementContext;
import io.sentry.protocol.MeasurementValue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MemoryMeasurementCollector extends BackgroundAwareMeasurementCollector {
  //  private final @NotNull Context applicationContext;
  private @NotNull final SentryOptions options;
  private @NotNull List<MeasurementBackgroundServiceType> listenToTypes;
  private @Nullable ActivityManager.MemoryInfo memoryAtStart;
  private long availMem;

  public MemoryMeasurementCollector(
      @NotNull SentryOptions options,
      @NotNull Context applicationContext,
      @NotNull MeasurementBackgroundService backgroundService) {
    super(backgroundService);
    this.options = options;
    this.listenToTypes = Arrays.asList(MeasurementBackgroundServiceType.MEMORY);
    //    this.applicationContext = applicationContext;
  }

  @Override
  protected List<MeasurementBackgroundServiceType> listenToTypes() {
    return listenToTypes;
  }

  @Override
  protected void onTransactionStartedInternal(@NotNull ITransaction transaction) {
    long start = System.currentTimeMillis();
    //    @Nullable ActivityManager am = (ActivityManager)
    // applicationContext.getSystemService(ACTIVITY_SERVICE);
    //    if (am != null) {
    //      ActivityManager.MemoryInfo memType = new ActivityManager.MemoryInfo();
    //      am.getMemoryInfo(memType);
    //      availMem = memType.availMem;
    //    }
    Runtime runtime = Runtime.getRuntime();
    availMem = runtime.totalMemory() - runtime.freeMemory();
    long delta = System.currentTimeMillis() - start;
    options.getLogger().log(SentryLevel.INFO, "mem took %d ms and had result %d", delta, availMem);

    memoryAtStart =
        (ActivityManager.MemoryInfo)
            backgroundService.getLatest(MeasurementBackgroundServiceType.MEMORY);
    if (memoryAtStart != null) {
      options.getLogger().log(SentryLevel.INFO, "from background %s", memoryAtStart.availMem);
    }
  }

  @Override
  protected @Nullable Map<String, MeasurementValue> onTransactionFinishedInternal(
      @NotNull ITransaction transaction, @NotNull MeasurementContext context) {
    Map<String, MeasurementValue> results = new HashMap<>();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      long start = System.currentTimeMillis();
      String bytesAllocatedString = Debug.getRuntimeStat("art.gc.bytes-allocated");
      long delta = System.currentTimeMillis() - start;
      Long value = Long.valueOf(bytesAllocatedString);
      options
          .getLogger()
          .log(SentryLevel.INFO, "mem from debug took %d ms and had result %d", delta, value);
      results.put("mem_bytes_allocated", new MeasurementValue(value));

      Double transactionDuration = context.getDuration();
      if (transactionDuration != null) {
        Double bytesAllocatedPerSecond = value.doubleValue() / transactionDuration;
        results.put(
            "mem_bytes_allocated_per_second",
            new MeasurementValue(bytesAllocatedPerSecond.longValue()));
      }
    }

    List<Object> from =
        backgroundService.getFrom(
            MeasurementBackgroundServiceType.MEMORY,
            startDate,
            backgroundService.getPollingInterval());
    options.getLogger().log(SentryLevel.INFO, "from background at end count %d", from.size());
    for (Object o : from) {
      ActivityManager.MemoryInfo mem = (ActivityManager.MemoryInfo) o;
      options.getLogger().log(SentryLevel.INFO, "from background at end %s", mem.availMem);
    }

    return results;
  }
}
