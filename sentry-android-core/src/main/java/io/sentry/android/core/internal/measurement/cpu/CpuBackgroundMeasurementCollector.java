package io.sentry.android.core.internal.measurement.cpu;

import android.annotation.SuppressLint;
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

      // TODO 2-4 ms on first read, then cached 4 - 20 μs
      List<Integer> maxFrequencies = CpuInfoUtils.getInstance().readMaxFrequencies();
      // TODO 0.5 - 6ms
      List<Integer> currentFrequencies = CpuInfoUtils.getInstance().readCurrentFrequencies();
      return new CpuReading(clockTicks, maxFrequencies, currentFrequencies);
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

    public CpuReading(
        @Nullable Long ticks,
        @NotNull List<Integer> maxFrequencies,
        @NotNull List<Integer> currentFrequencies) {
      this.ticks = ticks;
      this.maxFrequencies = maxFrequencies;
      this.currentFrequencies = currentFrequencies;
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
  }
}
