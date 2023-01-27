package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class MemoryCollectionData {
  final long timestampMillis;
  final long usedHeapMemory;
  final long usedNativeMemory;

  public MemoryCollectionData(
      final long timestampMillis, final long usedHeapMemory, final long usedNativeMemory) {
    this.timestampMillis = timestampMillis;
    this.usedHeapMemory = usedHeapMemory;
    this.usedNativeMemory = usedNativeMemory;
  }

  public MemoryCollectionData(final long timestampMillis, final long usedHeapMemory) {
    this(timestampMillis, usedHeapMemory, -1);
  }

  public long getTimestampMillis() {
    return timestampMillis;
  }

  public long getUsedHeapMemory() {
    return usedHeapMemory;
  }

  public long getUsedNativeMemory() {
    return usedNativeMemory;
  }
}
