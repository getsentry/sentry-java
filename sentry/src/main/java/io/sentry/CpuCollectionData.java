package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CpuCollectionData {
  final double cpuUsagePercentage;
  final @NotNull SentryDate timestamp;

  public CpuCollectionData(final double cpuUsagePercentage, final @NotNull SentryDate timestamp) {
    this.cpuUsagePercentage = cpuUsagePercentage;
    this.timestamp = timestamp;
  }

  public @NotNull SentryDate getTimestamp() {
    return timestamp;
  }

  public double getCpuUsagePercentage() {
    return cpuUsagePercentage;
  }
}
