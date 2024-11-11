package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class MemoryCollectionData {
  final long usedHeapMemory;
  final long usedNativeMemory;
  final @NotNull SentryDate timestamp;

  public MemoryCollectionData(
      final long usedHeapMemory, final long usedNativeMemory, final @NotNull SentryDate timestamp) {
    this.usedHeapMemory = usedHeapMemory;
    this.usedNativeMemory = usedNativeMemory;
    this.timestamp = timestamp;
  }

  public MemoryCollectionData(final long usedHeapMemory, final @NotNull SentryDate timestamp) {
    this(usedHeapMemory, -1, timestamp);
  }

  public @NotNull SentryDate getTimestamp() {
    return timestamp;
  }

  public long getUsedHeapMemory() {
    return usedHeapMemory;
  }

  public long getUsedNativeMemory() {
    return usedNativeMemory;
  }
}
