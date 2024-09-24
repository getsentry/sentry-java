package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class MemoryCollectionData {
  final long timestampMillis;
  final long usedHeapMemory;
  final long usedNativeMemory;
  final @NotNull SentryDate timestamp;

  public MemoryCollectionData(
      final long timestampMillis,
      final long usedHeapMemory,
      final long usedNativeMemory,
      final @NotNull SentryDate timestamp) {
    this.timestampMillis = timestampMillis;
    this.usedHeapMemory = usedHeapMemory;
    this.usedNativeMemory = usedNativeMemory;
    this.timestamp = timestamp;
  }

  public MemoryCollectionData(
      final long timestampMillis, final long usedHeapMemory, final @NotNull SentryDate timestamp) {
    this(timestampMillis, usedHeapMemory, -1, timestamp);
  }

  public @NotNull SentryDate getTimestamp() {
    return timestamp;
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
