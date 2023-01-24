package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class JavaMemoryCollector implements ICollector {

  private final @NotNull Runtime runtime = Runtime.getRuntime();

  @Override
  public void setup() {}

  @Override
  public void collect(@NotNull Iterable<PerformanceCollectionData> performanceCollectionData) {
    final long now = System.currentTimeMillis();
    final long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    MemoryCollectionData memoryData = new MemoryCollectionData(now, usedMemory);
    for (PerformanceCollectionData data : performanceCollectionData) {
      data.addMemoryData(memoryData);
    }
  }
}
