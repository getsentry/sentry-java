package io.sentry.android.core;

import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import io.sentry.ILogger;
import io.sentry.IPerformanceSnapshotCollector;
import io.sentry.PerformanceCollectionData;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

// The process cpu time comes from Process.getElapsedCpuTime(), a @CriticalNative wrapper around
// clock_gettime(CLOCK_PROCESS_CPUTIME_ID), rather than from parsing /proc/self/stat: reading and
// parsing that file allocated on every sample, and collect() runs 10 times per second for the whole
// duration of a transaction. It does not include the cpu time of reaped child processes, which an
// app process doesn't have.
@ApiStatus.Internal
public final class AndroidCpuCollector implements IPerformanceSnapshotCollector {

  private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000;

  private long lastRealtimeNanos = 0;
  private long lastCpuNanos = 0;

  private long numCores = 1;

  private boolean isEnabled = false;

  public AndroidCpuCollector(final @NotNull ILogger logger) {
    Objects.requireNonNull(logger, "Logger is required.");
  }

  @Override
  public void setup() {
    isEnabled = true;
    numCores = Os.sysconf(OsConstants._SC_NPROCESSORS_CONF);
    lastRealtimeNanos = SystemClock.elapsedRealtimeNanos();
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

    performanceCollectionData.setCpuUsagePercentage(
        (cpuUsagePercentage / (double) numCores) * 100.0);
  }

  private long readTotalCpuNanos() {
    return Process.getElapsedCpuTime() * NANOSECONDS_PER_MILLISECOND;
  }
}
