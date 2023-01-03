package io.sentry.android.core;

import android.os.Debug;
import io.sentry.IMemoryCollector;
import io.sentry.MemoryCollectionData;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class AndroidMemoryCollector implements IMemoryCollector {
  @Override
  public MemoryCollectionData collect() {
    long now = System.currentTimeMillis();
    long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    long usedNativeMemory = Debug.getNativeHeapSize() - Debug.getNativeHeapFreeSize();
    return new MemoryCollectionData(now, usedMemory, usedNativeMemory);
  }
}
