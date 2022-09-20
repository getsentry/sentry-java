package io.sentry.android.core.internal.measurement.cpu;

import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
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
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class CpuMeasurementCollector extends BackgroundAwareMeasurementCollector {

  private final SentryOptions options;
  private List<MeasurementBackgroundServiceType> listenToTypes;
  private @Nullable Long startRealtimeNanos;
  private @Nullable Long startElapsedTimeMs;

  public CpuMeasurementCollector(
      @NotNull SentryOptions options, @NotNull MeasurementBackgroundService backgroundService) {
    super(backgroundService);
    this.options = options;
    this.listenToTypes = Arrays.asList(MeasurementBackgroundServiceType.CPU);
  }

  @Override
  protected List<MeasurementBackgroundServiceType> listenToTypes() {
    return listenToTypes;
  }

  @Override
  protected void onTransactionStartedInternal(@NotNull ITransaction transaction) {
    // TODO 8 - 23 μs
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      startRealtimeNanos = SystemClock.elapsedRealtimeNanos();
      startElapsedTimeMs = Process.getElapsedCpuTime();
    }
  }

  @Override
  protected @Nullable Map<String, MeasurementValue> onTransactionFinishedInternal(
      @NotNull ITransaction transaction, @NotNull MeasurementContext context) {
    HashMap<String, MeasurementValue> results = new HashMap<>();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
        && startRealtimeNanos != null
        && startElapsedTimeMs != null) {
      // TODO SystemClock.elapsedRealtimeNanos() + Process.getElapsedCpuTime() take 9 - 15 μs
      Long diffRealtimeNanos = SystemClock.elapsedRealtimeNanos() - startRealtimeNanos;
      Long diffElapsedTimeMs = Process.getElapsedCpuTime() - startElapsedTimeMs;
      results.put(
          "cpu_realtime_diff_ns",
          new MeasurementValue(diffRealtimeNanos, MeasurementValue.NANOSECOND_UNIT));
      results.put(
          "cpu_elapsed_time_diff_ms",
          new MeasurementValue(diffElapsedTimeMs, MeasurementValue.MILLISECOND_UNIT));
      Double ratio =
          diffElapsedTimeMs.doubleValue()
              * TimeUnit.MILLISECONDS.toNanos(1)
              / diffRealtimeNanos.doubleValue();
      results.put(
          "cpu_time_ratio", new MeasurementValue(ratio.floatValue(), MeasurementValue.RATIO_UNIT));
    }

    List<Object> values =
        backgroundService.getFrom(
            MeasurementBackgroundServiceType.CPU,
            startDate,
            backgroundService.getPollingInterval());

    options.getLogger().log(SentryLevel.INFO, "CPU mc got %d values from bg", values.size());

    if (values.size() >= 2) {
      CpuBackgroundMeasurementCollector.CpuReading cpuReadingAtStart =
          (CpuBackgroundMeasurementCollector.CpuReading) values.get(0);
      CpuBackgroundMeasurementCollector.CpuReading cpuReadingAtEnd =
          (CpuBackgroundMeasurementCollector.CpuReading) values.get(values.size() - 1);
      long ticksDelta = cpuReadingAtEnd.getTicks() - cpuReadingAtStart.getTicks();
      // TODO 0.1 - 5ms
      Integer cpuCores = cpuReadingAtStart.getCpuCores();
      if (cpuCores != null) {
        results.put(
            "cpu_cores_file_system", new MeasurementValue(cpuCores, MeasurementValue.NONE_UNIT));
      }

      results.put("cpu_ticks", new MeasurementValue(ticksDelta, MeasurementValue.NONE_UNIT));

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        long numberOfCores = Os.sysconf(OsConstants._SC_NPROCESSORS_CONF);
        results.put("cpu_cores", new MeasurementValue(numberOfCores, MeasurementValue.NONE_UNIT));

        // TODO 6 - 9 μs
        long clockTickHz = Os.sysconf(OsConstants._SC_CLK_TCK);
        options.getLogger().log(SentryLevel.WARNING, "hz = %d", clockTickHz);
        Double cpuTimeSeconds = Double.valueOf(ticksDelta) / Double.valueOf(clockTickHz);
        float cpuTimeMs = Double.valueOf(cpuTimeSeconds * 1000.0).floatValue();
        results.put(
            "cpu_time_ms", new MeasurementValue(cpuTimeMs, MeasurementValue.MILLISECOND_UNIT));

        @Nullable Double transactionDuration = context.getDuration();
        if (transactionDuration != null) {
          results.put(
              "transaction_duration_ms",
              new MeasurementValue(
                  Double.valueOf(transactionDuration * 1000.0).floatValue(),
                  MeasurementValue.MILLISECOND_UNIT));
          Double factor = cpuTimeSeconds / transactionDuration;
          // TODO name
          results.put(
              "cpu_busy_factor",
              new MeasurementValue(factor.floatValue(), MeasurementValue.RATIO_UNIT));

          Double altFactor = Double.valueOf(ticksDelta) / transactionDuration;
          results.put(
              "cpu_busy_factor_alt",
              new MeasurementValue(altFactor.floatValue(), MeasurementValue.RATIO_UNIT));
        }
      }
    } else {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Did not get enough CPU background measurement values to calculate something.");
    }

    return results;
  }
}
