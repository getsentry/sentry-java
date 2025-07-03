package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PerformanceCollectionData {
  private @Nullable Double cpuUsagePercentage = null;
  private @Nullable Long usedHeapMemory = null;
  private @Nullable Long usedNativeMemory = null;
  private final long nanoTimestamp;

  public PerformanceCollectionData(final long nanoTimestamp) {
    this.nanoTimestamp = nanoTimestamp;
  }

  /** Set the cpu usage percentage. */
  public void setCpuUsagePercentage(final @Nullable Double cpuUsagePercentage) {
    this.cpuUsagePercentage = cpuUsagePercentage;
  }

  public @Nullable Double getCpuUsagePercentage() {
    return cpuUsagePercentage;
  }

  public void setUsedHeapMemory(final @Nullable Long usedHeapMemory) {
    this.usedHeapMemory = usedHeapMemory;
  }

  public @Nullable Long getUsedHeapMemory() {
    return usedHeapMemory;
  }

  public void setUsedNativeMemory(final @Nullable Long usedNativeMemory) {
    this.usedNativeMemory = usedNativeMemory;
  }

  public @Nullable Long getUsedNativeMemory() {
    return usedNativeMemory;
  }

  public long getNanoTimestamp() {
    return nanoTimestamp;
  }
}
