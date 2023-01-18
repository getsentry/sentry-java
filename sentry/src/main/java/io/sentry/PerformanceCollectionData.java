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

  public void addMemoryData(final @Nullable MemoryCollectionData memoryCollectionData) {
    if (memoryCollectionData != null) {
      uncommittedMemoryData = memoryCollectionData;
    }
  }

  public void addCpuData(final @Nullable CpuCollectionData cpuCollectionData) {
    if (cpuCollectionData != null) {
      uncommittedCpuData = cpuCollectionData;
    }
  }

  public void commitData() {
    if (uncommittedMemoryData != null) {
      memoryData.add(uncommittedMemoryData);
    }
    if (uncommittedCpuData != null) {
      cpuData.add(uncommittedCpuData);
    }
  }

  public @NotNull List<CpuCollectionData> getCpuData() {
    return cpuData;
  }

  public @NotNull List<MemoryCollectionData> getMemoryData() {
    return memoryData;
  }
}
