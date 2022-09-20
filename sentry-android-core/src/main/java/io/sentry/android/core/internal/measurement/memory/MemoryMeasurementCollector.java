package io.sentry.android.core.internal.measurement.memory;

import android.annotation.SuppressLint;
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

  @SuppressLint("NewApi")
  @Override
  protected void onTransactionStartedInternal(@NotNull ITransaction transaction) {
    // TODO 1 - 5 μs
    //    Runtime runtime = Runtime.getRuntime();
  }

  @Override
  protected @Nullable Map<String, MeasurementValue> onTransactionFinishedInternal(
      @NotNull ITransaction transaction, @NotNull MeasurementContext context) {
    Map<String, MeasurementValue> results = new HashMap<>();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // TODO 7 - 18 μs
      String bytesAllocatedString = Debug.getRuntimeStat("art.gc.bytes-allocated");
      Long value = Long.valueOf(bytesAllocatedString);
      results.put("mem_bytes_allocated", new MeasurementValue(value, MeasurementValue.BYTE_UNIT));

      Double transactionDuration = context.getDuration();
      if (transactionDuration != null) {
        Double bytesAllocatedPerSecond = value.doubleValue() / transactionDuration;
        results.put(
            "mem_bytes_allocated_per_second",
            new MeasurementValue(bytesAllocatedPerSecond.floatValue(), MeasurementValue.BYTE_UNIT));
      }
    }

    List<Object> from =
        backgroundService.getFrom(
            MeasurementBackgroundServiceType.MEMORY,
            startDate,
            backgroundService.getPollingInterval());
    options.getLogger().log(SentryLevel.INFO, "from background at end count %d", from.size());
    for (Object o : from) {
      MemoryBackgroundMeasurementCollector.MemoryReading mem =
          (MemoryBackgroundMeasurementCollector.MemoryReading) o;
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "from background at end am: %,d/%,d | runtime: %,d/%,d|%,d | pss: %,d | procstat: %,d",
              mem.getAm(),
              mem.getAmTotal(),
              mem.getRuntime(),
              mem.getRuntimeTotal(),
              mem.getRuntimeMax(),
              mem.getPss(),
              mem.getProcStatusMemory());
    }

    return results;
  }
}
