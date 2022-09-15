package io.sentry.android.core.internal.measurement.memory;

import static android.content.Context.ACTIVITY_SERVICE;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.measurement.MeasurementBackgroundCollector;
import io.sentry.measurement.MeasurementBackgroundServiceType;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MemoryBackgroundMeasurementCollector implements MeasurementBackgroundCollector {

  private final @NotNull Context applicationContext;
  private final @NotNull SentryOptions options;
  private final @NotNull Pattern procStatusMemoryRegex =
      Pattern.compile("VmRSS:\\s*?(\\d+)\\s*?\\w*");

  public MemoryBackgroundMeasurementCollector(
      @NotNull Context applicationContext, @NotNull SentryOptions options) {
    this.applicationContext = applicationContext;
    this.options = options;
  }

  @Override
  public @NotNull MeasurementBackgroundServiceType getMeasurementType() {
    return MeasurementBackgroundServiceType.MEMORY;
  }

  @Override
  @SuppressLint("NewApi")
  public @Nullable Object collect() {
    @Nullable Long procStatusMemory = null;
    try {
      List<String> statLines = readProcSelfStatus();
      for (String statLine : statLines) {
        Matcher matcher = procStatusMemoryRegex.matcher(statLine);
        if (matcher.matches()) {
          procStatusMemory = Long.valueOf(matcher.group(1));
          break;
        }
      }
    } catch (Exception e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Unable to collect CPU measurement in background", e);
      return null;
    }

    // TODO 0.3 - 5 ms
    @Nullable
    ActivityManager am = (ActivityManager) applicationContext.getSystemService(ACTIVITY_SERVICE);
    if (am != null) {
      // TODO 0.1 - 10 ms
      ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
      am.getMemoryInfo(memInfo);
      long freeMem = memInfo.totalMem - memInfo.availMem;

      // TODO 9 - 31 ms
      Debug.MemoryInfo debugMemInfo = new Debug.MemoryInfo();
      Debug.getMemoryInfo(debugMemInfo);
      long pss = debugMemInfo.getTotalPss();

      // TODO 1 - 5 μs
      Runtime runtime = Runtime.getRuntime();
      return new MemoryReading(
          freeMem,
          memInfo.totalMem,
          runtime.freeMemory(),
          runtime.totalMemory(),
          runtime.maxMemory(),
          pss,
          procStatusMemory);
    }

    return null;
  }

  private List<String> readProcSelfStatus() throws IOException {
    List<String> lines = new ArrayList<>();
    try (RandomAccessFile reader = new RandomAccessFile("/proc/self/status", "r")) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
      return lines;
    }
  }

  public static final class MemoryReading {
    private final long am;
    private final long amTotal;
    private final long runtime;
    private final long runtimeTotal;
    private final long runtimeMax;
    private final long pss;
    private final @Nullable Long procStatusMemory;

    public MemoryReading(
        long am,
        long amTotal,
        long runtime,
        long runtimeTotal,
        long runtimeMax,
        long pss,
        @Nullable Long procStatusMemory) {
      this.am = am;
      this.amTotal = amTotal;
      this.runtime = runtime;
      this.runtimeTotal = runtimeTotal;
      this.runtimeMax = runtimeMax;
      this.pss = pss;
      this.procStatusMemory = procStatusMemory;
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

    public @Nullable Long getProcStatusMemory() {
      return procStatusMemory;
    }

    public long getPss() {
      return pss;
    }
  }
}
