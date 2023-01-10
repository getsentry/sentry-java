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

  public void addData(
      final @Nullable MemoryCollectionData memoryCollectionData,
      final @Nullable CpuCollectionData cpuCollectionData) {
    if (memoryCollectionData != null) {
      memoryData.add(memoryCollectionData);
    }
    if (cpuCollectionData != null) {
      cpuData.add(cpuCollectionData);
    }
  }

  public @NotNull List<CpuCollectionData> getCpuData() {
    return cpuData;
  }

  public @NotNull List<MemoryCollectionData> getMemoryData() {
    return memoryData;
  }
}
