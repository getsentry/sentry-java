package io.sentry.android.core.internal.measurement.cpu;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.measurement.MeasurementBackgroundCollector;
import io.sentry.measurement.MeasurementBackgroundServiceType;
import java.io.IOException;
import java.io.RandomAccessFile;
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
  public @Nullable Object collect() {
    try {
      // TODO 1-5 ms
      String stat = readProcSelfStat();
      return parseClockTicks(stat);
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
}
