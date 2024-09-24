package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CpuCollectionData {
  final long timestampMillis;
  final double cpuUsagePercentage;
  final @NotNull SentryDate timestamp;

  public CpuCollectionData(
      final long timestampMillis,
      final double cpuUsagePercentage,
      final @NotNull SentryDate timestamp) {
    this.timestampMillis = timestampMillis;
    this.cpuUsagePercentage = cpuUsagePercentage;
    this.timestamp = timestamp;
  }

  public @NotNull SentryDate getTimestamp() {
    return timestamp;
  }

  public long getTimestampMillis() {
    return timestampMillis;
  }

  public double getCpuUsagePercentage() {
    return cpuUsagePercentage;
  }
}
