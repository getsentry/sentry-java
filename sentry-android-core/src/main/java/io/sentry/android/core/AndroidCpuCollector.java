package io.sentry.android.core;

import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import io.sentry.CpuCollectionData;
import io.sentry.ILogger;
import io.sentry.IPerformanceSnapshotCollector;
import io.sentry.PerformanceCollectionData;
import io.sentry.SentryLevel;
import io.sentry.util.FileUtils;
import io.sentry.util.Objects;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

// The approach to get the cpu usage info was taken from
// https://eng.lyft.com/monitoring-cpu-performance-of-lyfts-android-applications-4e36fafffe12
// The content of the /proc/self/stat file is specified in
// https://man7.org/linux/man-pages/man5/proc.5.html
@ApiStatus.Internal
public final class AndroidCpuCollector implements IPerformanceSnapshotCollector {

  private long lastRealtimeNanos = 0;
  private long lastCpuNanos = 0;

  /** Number of clock ticks per second. */
  private long clockSpeedHz = 1;

  private long numCores = 1;
  private final long NANOSECOND_PER_SECOND = 1_000_000_000;

  /** Number of nanoseconds per clock tick. */
  private double nanosecondsPerClockTick = NANOSECOND_PER_SECOND / (double) clockSpeedHz;

  /** File containing stats about this process. */
  private final @NotNull File selfStat = new File("/proc/self/stat");

  private final @NotNull ILogger logger;
  private boolean isEnabled = false;
  private final @NotNull Pattern newLinePattern = Pattern.compile("[\n\t\r ]");

  public AndroidCpuCollector(final @NotNull ILogger logger) {
    this.logger = Objects.requireNonNull(logger, "Logger is required.");
  }

  @Override
  public void setup() {
    isEnabled = true;
    clockSpeedHz = Os.sysconf(OsConstants._SC_CLK_TCK);
    numCores = Os.sysconf(OsConstants._SC_NPROCESSORS_CONF);
    nanosecondsPerClockTick = NANOSECOND_PER_SECOND / (double) clockSpeedHz;
    lastCpuNanos = readTotalCpuNanos();
  }

  @Override
  public void collect(final @NotNull PerformanceCollectionData performanceCollectionData) {
    if (!isEnabled) {
      return;
    }
    final long nowNanos = SystemClock.elapsedRealtimeNanos();
    final long realTimeNanosDiff = nowNanos - lastRealtimeNanos;
    lastRealtimeNanos = nowNanos;
    final long cpuNanos = readTotalCpuNanos();
    final long cpuNanosDiff = cpuNanos - lastCpuNanos;
    lastCpuNanos = cpuNanos;
    // Later we need to divide the percentage by the number of cores, otherwise we could
    // get a percentage value higher than 1. We also want to send the percentage as a
    // number from 0 to 100, so we are going to multiply it by 100
    final double cpuUsagePercentage = cpuNanosDiff / (double) realTimeNanosDiff;

    CpuCollectionData cpuData =
        new CpuCollectionData(
            System.currentTimeMillis(), (cpuUsagePercentage / (double) numCores) * 100.0);

    performanceCollectionData.addCpuData(cpuData);
  }

  /** Read the /proc/self/stat file and parses the result. */
  private long readTotalCpuNanos() {
    String stat = null;
    try {
      stat = FileUtils.readText(selfStat);
    } catch (IOException e) {
      // If an error occurs when reading the file, we avoid reading it again until the setup method
      // is called again
      isEnabled = false;
      logger.log(
          SentryLevel.WARNING, "Unable to read /proc/self/stat file. Disabling cpu collection.", e);
    }
    if (stat != null) {
      stat = stat.trim();
      String[] stats = newLinePattern.split(stat);
      try {
        // Amount of clock ticks this process has been scheduled in user mode
        long uTime = Long.parseLong(stats[13]);
        // Amount of clock ticks this process has been scheduled in kernel mode
        long sTime = Long.parseLong(stats[14]);
        // Amount of clock ticks this process' waited-for children has been scheduled in user mode
        long cuTime = Long.parseLong(stats[15]);
        // Amount of clock ticks this process' waited-for children has been scheduled in kernel mode
        long csTime = Long.parseLong(stats[16]);
        return (long) ((uTime + sTime + cuTime + csTime) * nanosecondsPerClockTick);
      } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
        logger.log(SentryLevel.ERROR, "Error parsing /proc/self/stat file.", e);
        return 0;
      }
    }
    return 0;
  }
}
