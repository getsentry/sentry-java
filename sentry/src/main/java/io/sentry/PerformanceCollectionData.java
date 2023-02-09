package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PerformanceCollectionData {
  private @Nullable MemoryCollectionData memoryData = null;
  private @Nullable CpuCollectionData cpuData = null;

  /** Store a {@link io.sentry.MemoryCollectionData}, if not null. */
  public void addMemoryData(final @Nullable MemoryCollectionData memoryCollectionData) {
    if (memoryCollectionData != null) {
      memoryData = memoryCollectionData;
    }
  }

  /** Store a {@link io.sentry.CpuCollectionData}, if not null. */
  public void addCpuData(final @Nullable CpuCollectionData cpuCollectionData) {
    if (cpuCollectionData != null) {
      cpuData = cpuCollectionData;
    }
  }

  public @Nullable CpuCollectionData getCpuData() {
    return cpuData;
  }

  public @Nullable MemoryCollectionData getMemoryData() {
    return memoryData;
  }
}
