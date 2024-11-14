package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class JavaMemoryCollector implements IPerformanceSnapshotCollector {

  private final @NotNull Runtime runtime = Runtime.getRuntime();

  @Override
  public void setup() {}

  @Override
  public void collect(final @NotNull PerformanceCollectionData performanceCollectionData) {
    final long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    MemoryCollectionData memoryData =
        new MemoryCollectionData(usedMemory, new SentryNanotimeDate());
    performanceCollectionData.addMemoryData(memoryData);
  }
}
