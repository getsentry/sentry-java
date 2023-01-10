package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class MemoryCollectionData {
  final long timestamp;
  final long usedHeapMemory;
  final long usedNativeMemory;

  public MemoryCollectionData(
      final long timestamp, final long usedHeapMemory, final long usedNativeMemory) {
    this.timestamp = timestamp;
    this.usedHeapMemory = usedHeapMemory;
    this.usedNativeMemory = usedNativeMemory;
  }

  public MemoryCollectionData(final long timestamp, final long usedHeapMemory) {
    this(timestamp, usedHeapMemory, -1);
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getUsedHeapMemory() {
    return usedHeapMemory;
  }

  public long getUsedNativeMemory() {
    return usedNativeMemory;
  }
}
