package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class CpuCollectionData {
  final long timestampMillis;
  final double cpuUsagePercentage;

  public CpuCollectionData(final long timestampMillis, final double cpuUsagePercentage) {
    this.timestampMillis = timestampMillis;
    this.cpuUsagePercentage = cpuUsagePercentage;
  }

  public long getTimestampMillis() {
    return timestampMillis;
  }

  public double getCpuUsagePercentage() {
    return cpuUsagePercentage;
  }
}
