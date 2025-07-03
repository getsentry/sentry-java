package io.sentry.android.core;

import android.os.Debug;
import io.sentry.IPerformanceSnapshotCollector;
import io.sentry.PerformanceCollectionData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class AndroidMemoryCollector implements IPerformanceSnapshotCollector {

  @Override
  public void setup() {}

  @Override
  public void collect(final @NotNull PerformanceCollectionData performanceCollectionData) {
    long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    long usedNativeMemory = Debug.getNativeHeapSize() - Debug.getNativeHeapFreeSize();
    performanceCollectionData.setUsedHeapMemory(usedMemory);
    performanceCollectionData.setUsedNativeMemory(usedNativeMemory);
  }
}
