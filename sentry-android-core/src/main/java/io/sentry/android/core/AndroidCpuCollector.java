package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import io.sentry.CpuCollectionData;
import io.sentry.ICpuCollector;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.util.FileUtils;
import io.sentry.util.Objects;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// The approach to get the cpu usage info was taken from
// https://eng.lyft.com/monitoring-cpu-performance-of-lyfts-android-applications-4e36fafffe12
// The content of the /proc/self/stat file is specified in
// https://man7.org/linux/man-pages/man5/proc.5.html
@ApiStatus.Internal
public final class AndroidCpuCollector implements ICpuCollector {

  private long lastRealtimeNanos = 0;
  private long lastCpuNanos = 0;
  private long clockSpeedHz = 1;
  private long numCores = 1;
  private final long NANOSECOND_PER_SECOND = 1_000_000_000;
  private double nanosecondsPerClockTick = NANOSECOND_PER_SECOND / (double) clockSpeedHz;
  private final @NotNull File selfStat = new File("/proc/self/stat");
  private final @NotNull ILogger logger;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private boolean isEnabled = false;

  public AndroidCpuCollector(
      final @NotNull ILogger logger, final @NotNull BuildInfoProvider buildInfoProvider) {
    this.logger = Objects.requireNonNull(logger, "Logger is required.");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required.");
  }

  @SuppressLint("NewApi")
  @Override
  public void setup() {
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP) {
      isEnabled = false;
      return;
    }
    isEnabled = true;
    clockSpeedHz = Os.sysconf(OsConstants._SC_CLK_TCK);
    numCores = Os.sysconf(OsConstants._SC_NPROCESSORS_CONF);
    nanosecondsPerClockTick = NANOSECOND_PER_SECOND / (double) clockSpeedHz;
    lastCpuNanos = readTotalCpuNanos();
  }

  @SuppressLint("NewApi")
  @Override
  public @Nullable CpuCollectionData collect() {
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.LOLLIPOP || !isEnabled) {
      return null;
    }
    long nowNanos = SystemClock.elapsedRealtimeNanos();
    long realTimeNanosDiff = nowNanos - lastRealtimeNanos;
    lastRealtimeNanos = nowNanos;
    long cpuNanos = readTotalCpuNanos();
    long cpuNanosDiff = cpuNanos - lastCpuNanos;
    lastCpuNanos = cpuNanos;

    return new CpuCollectionData(
        System.currentTimeMillis(),
        (cpuNanosDiff / (double) realTimeNanosDiff) / (double) numCores);
  }

  private long readTotalCpuNanos() {
    String stat = null;
    try {
      stat = FileUtils.readText(selfStat);
    } catch (IOException e) {
      isEnabled = false;
      logger.log(
          SentryLevel.ERROR, "Unable to read /proc/self/stat file. Disabling cpu collection.", e);
    }
    if (stat != null) {
      stat = stat.trim();
      String[] stats = stat.split("[\n\t\r ]");
      long uTime = Long.parseLong(stats[13]);
      long sTime = Long.parseLong(stats[14]);
      long cuTime = Long.parseLong(stats[15]);
      long csTime = Long.parseLong(stats[16]);
      return (long) ((uTime + sTime + cuTime + csTime) * nanosecondsPerClockTick);
    }
    return 0;
  }
}
