package io.sentry.android.core;

import android.os.Debug;
import io.sentry.ICollector;
import io.sentry.MemoryCollectionData;
import io.sentry.PerformanceCollectionData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class AndroidMemoryCollector implements ICollector {

  @Override
  public void setup() {}

  @Override
  public void collect(@NotNull Iterable<PerformanceCollectionData> performanceCollectionData) {
    long now = System.currentTimeMillis();
    long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    long usedNativeMemory = Debug.getNativeHeapSize() - Debug.getNativeHeapFreeSize();
    MemoryCollectionData memoryData = new MemoryCollectionData(now, usedMemory, usedNativeMemory);
    for (PerformanceCollectionData data : performanceCollectionData) {
      data.addMemoryData(memoryData);
    }
  }
}
