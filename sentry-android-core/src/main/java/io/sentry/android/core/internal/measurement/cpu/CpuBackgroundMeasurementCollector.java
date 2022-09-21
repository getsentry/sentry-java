package io.sentry.android.core.internal.measurement.cpu;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.util.CpuInfoUtils;
import io.sentry.measurement.MeasurementBackgroundCollector;
import io.sentry.measurement.MeasurementBackgroundServiceType;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CpuBackgroundMeasurementCollector implements MeasurementBackgroundCollector {

  private final SentryOptions options;

  public CpuBackgroundMeasurementCollector(@NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @NotNull MeasurementBackgroundServiceType getMeasurementType() {
    return MeasurementBackgroundServiceType.CPU;
  }

  @Override
  @SuppressLint("NewApi")
  public @Nullable Object collect() {
    try {
      // TODO 1-5 ms
      String stat = readProcSelfStat();
      Long clockTicks = parseClockTicks(stat);
      // TODO pull out to cache or even easier re-use?
      //      @Nullable Boolean isEmulator = new
      // BuildInfoProvider(options.getLogger()).isEmulator();
      //      if (isEmulator != null && isEmulator == true) {
      //        options.getLogger().log(SentryLevel.DEBUG, "Not collecting CPU frequencies as
      // thisi devicie seems to be a simulator.");
      //        return new CpuReading(clockTicks, new ArrayList<>(), new ArrayList<>(), -1);
      //      } else {
      // TODO 2-4 ms on first read, then cached 4 - 20 μs
      List<Integer> maxFrequencies = CpuInfoUtils.getInstance().readMaxFrequencies();
      // TODO 0.5 - 6ms
      List<Integer> currentFrequencies = CpuInfoUtils.getInstance().readCurrentFrequencies();
      Long start = SystemClock.elapsedRealtimeNanos();
      Integer numberOfCores = CpuInfoUtils.getInstance().readNumberOfCores();
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Readig number of cores took %,d ns and returned %d vs %d vs %d",
              SystemClock.elapsedRealtimeNanos() - start,
              numberOfCores,
              Os.sysconf(OsConstants._SC_NPROCESSORS_CONF),
              Runtime.getRuntime().availableProcessors());
      return new CpuReading(clockTicks, maxFrequencies, currentFrequencies, numberOfCores);
      //      }
    } catch (IOException e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Unable to collect CPU measurement in background", e);
      return null;
    }
  }

  private String readProcSelfStat() throws IOException {
    try (RandomAccessFile reader = new RandomAccessFile("/proc/self/stat", "r")) {
      StringBuilder content = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        content.append(line);
        content.append('\n');
      }
      return content.toString();
    }
  }

  private @Nullable Long parseClockTicks(String statString) {
    String[] split = statString.split(" ", -1);
    return parseClockTicks(split);
  }

  private @Nullable Long parseClockTicks(String[] split) {
    // TODO make safe
    String clockTicksUserModeString = split[13];
    String clockTicksKernelModeString = split[14];
    String clockTicksUserModeChildrenString = split[15];
    String clockTicksKernelModeChildrenString = split[16];
    Long clockTicksUserMode = Long.valueOf(clockTicksUserModeString);
    Long clockTicksKernelMode = Long.valueOf(clockTicksKernelModeString);
    Long clockTicksUserModeChildren = Long.valueOf(clockTicksUserModeChildrenString);
    Long clockTicksKernelModeChildren = Long.valueOf(clockTicksKernelModeChildrenString);
    Long clockTicks =
        clockTicksUserMode
            + clockTicksKernelMode
            + clockTicksUserModeChildren
            + clockTicksKernelModeChildren;
    return clockTicks;
  }

  public static final class CpuReading {
    private final @Nullable Long ticks;
    private final @NotNull List<Integer> maxFrequencies;
    private final @NotNull List<Integer> currentFrequencies;
    private final @Nullable Integer cpuCores;

    public CpuReading(
        @Nullable Long ticks,
        @NotNull List<Integer> maxFrequencies,
        @NotNull List<Integer> currentFrequencies,
        @Nullable Integer cpuCores) {
      this.ticks = ticks;
      this.maxFrequencies = maxFrequencies;
      this.currentFrequencies = currentFrequencies;
      this.cpuCores = cpuCores;
    }

    public @NotNull List<Integer> getMaxFrequencies() {
      return maxFrequencies;
    }

    public @NotNull List<Integer> getCurrentFrequencies() {
      return currentFrequencies;
    }

    public @Nullable Long getTicks() {
      return ticks;
    }

    public @Nullable Integer getCpuCores() {
      return cpuCores;
    }
  }
}
