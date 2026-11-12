package io.sentry;

import org.jetbrains.annotations.ApiStatus;

/**
 * Holds a single performance measurement sample.
 *
 * <p>Measurements are stored as primitives with a separate presence flag, rather than as boxed
 * nullable types, because an instance is created every 100ms for as long as a transaction or
 * profile chunk is running.
 */
@ApiStatus.Internal
public final class PerformanceCollectionData {
  private double cpuUsagePercentage;
  private boolean hasCpuUsagePercentage;
  private long usedHeapMemory;
  private boolean hasUsedHeapMemory;
  private long usedNativeMemory;
  private boolean hasUsedNativeMemory;
  private final long nanoTimestamp;
  private final long tickNanos;

  /**
   * @param nanoTimestamp when the sample was taken, in epoch nanoseconds
   * @param tickNanos when the sample was taken, on {@link SentryOptions#getMonotonicTicker()}.
   *     Profilers place samples on their timeline with it, because the wall clock can step while a
   *     profile runs.
   */
  public PerformanceCollectionData(final long nanoTimestamp, final long tickNanos) {
    this.nanoTimestamp = nanoTimestamp;
    this.tickNanos = tickNanos;
  }

  /** Set the cpu usage percentage. */
  public void setCpuUsagePercentage(final double cpuUsagePercentage) {
    this.cpuUsagePercentage = cpuUsagePercentage;
    this.hasCpuUsagePercentage = true;
  }

  /** Only meaningful when {@link #hasCpuUsagePercentage()} is true. */
  public double getCpuUsagePercentage() {
    return cpuUsagePercentage;
  }

  public boolean hasCpuUsagePercentage() {
    return hasCpuUsagePercentage;
  }

  public void setUsedHeapMemory(final long usedHeapMemory) {
    this.usedHeapMemory = usedHeapMemory;
    this.hasUsedHeapMemory = true;
  }

  /** Only meaningful when {@link #hasUsedHeapMemory()} is true. */
  public long getUsedHeapMemory() {
    return usedHeapMemory;
  }

  public boolean hasUsedHeapMemory() {
    return hasUsedHeapMemory;
  }

  public void setUsedNativeMemory(final long usedNativeMemory) {
    this.usedNativeMemory = usedNativeMemory;
    this.hasUsedNativeMemory = true;
  }

  /** Only meaningful when {@link #hasUsedNativeMemory()} is true. */
  public long getUsedNativeMemory() {
    return usedNativeMemory;
  }

  public boolean hasUsedNativeMemory() {
    return hasUsedNativeMemory;
  }

  public long getNanoTimestamp() {
    return nanoTimestamp;
  }

  public long getTickNanos() {
    return tickNanos;
  }
}
