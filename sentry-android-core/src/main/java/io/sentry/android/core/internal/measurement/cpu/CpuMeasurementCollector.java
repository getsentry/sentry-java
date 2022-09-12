package io.sentry.android.core.internal.measurement.cpu;

import android.os.Build;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class CpuMeasurementCollector extends BackgroundAwareMeasurementCollector {

  private final SentryOptions options;
  private List<MeasurementBackgroundServiceType> listenToTypes;

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
  protected void onTransactionStartedInternal(@NotNull ITransaction transaction) {}

  @Override
  protected @Nullable Map<String, MeasurementValue> onTransactionFinishedInternal(
      @NotNull ITransaction transaction, @NotNull MeasurementContext context) {
    HashMap<String, MeasurementValue> results = new HashMap<>();
    List<Object> values =
        backgroundService.getFrom(
            MeasurementBackgroundServiceType.CPU,
            startDate,
            backgroundService.getPollingInterval());

    options.getLogger().log(SentryLevel.INFO, "CPU mc got %d values from bg", values.size());

    if (values.size() >= 2) {
      long ticksAtStart = (long) values.get(0);
      long ticksAtEnd = (long) values.get(values.size() - 1);
      long ticksDelta = ticksAtEnd - ticksAtStart;

      results.put("cpu_ticks", new MeasurementValue(ticksDelta));

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        long clockTickHz = Os.sysconf(OsConstants._SC_CLK_TCK);
        Double cpuTimeSeconds = Double.valueOf(ticksDelta) / Double.valueOf(clockTickHz);
        long cpuTimeMs = Double.valueOf(cpuTimeSeconds * 1000.0).longValue();
        results.put("cpu_time_ms", new MeasurementValue(cpuTimeMs));

        @Nullable Double transactionDuration = context.getDuration();
        if (transactionDuration != null) {
          results.put(
              "transaction_duration_ms",
              new MeasurementValue(Double.valueOf(transactionDuration * 1000.0).longValue()));
          Double factor = (cpuTimeSeconds / transactionDuration) * 100.0;
          // TODO name
          results.put("cpu_busy_percent", new MeasurementValue(factor.longValue()));
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
