package io.sentry;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PerformanceCollectionData {
  private final @NotNull List<MemoryCollectionData> memoryData = new ArrayList<>();
  private final @NotNull List<CpuCollectionData> cpuData = new ArrayList<>();
  private @Nullable MemoryCollectionData uncommittedMemoryData = null;
  private @Nullable CpuCollectionData uncommittedCpuData = null;

  /**
   * Add a {@link io.sentry.MemoryCollectionData} to internal uncommitted data. To save the data
   * call {@code commitData}. Only the last uncommitted memory data will be retained.
   */
  public void addMemoryData(final @Nullable MemoryCollectionData memoryCollectionData) {
    if (memoryCollectionData != null) {
      uncommittedMemoryData = memoryCollectionData;
    }
  }

  /**
   * Add a {@link io.sentry.CpuCollectionData} to internal uncommitted data. To save the data call
   * {@code commitData()}. Only the last uncommitted cpu data will be retained.
   */
  public void addCpuData(final @Nullable CpuCollectionData cpuCollectionData) {
    if (cpuCollectionData != null) {
      uncommittedCpuData = cpuCollectionData;
    }
  }

  /** Save any uncommitted data. */
  public void commitData() {
    if (uncommittedMemoryData != null) {
      memoryData.add(uncommittedMemoryData);
      uncommittedMemoryData = null;
    }
    if (uncommittedCpuData != null) {
      cpuData.add(uncommittedCpuData);
      uncommittedCpuData = null;
    }
  }

  public @NotNull List<CpuCollectionData> getCpuData() {
    return cpuData;
  }

  public @NotNull List<MemoryCollectionData> getMemoryData() {
    return memoryData;
  }
}
