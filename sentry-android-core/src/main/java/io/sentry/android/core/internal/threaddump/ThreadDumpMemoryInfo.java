package io.sentry.android.core.internal.threaddump;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Holds memory and GC metrics parsed from an ANRv2 thread dump.
 *
 * <p>Memory sizes are in bytes. GC times are in milliseconds.
 */
@ApiStatus.Internal
public final class ThreadDumpMemoryInfo {

  private @Nullable Long freeMemoryBytes;
  private @Nullable Long freeMemoryUntilGcBytes;
  private @Nullable Long freeMemoryUntilOOMEBytes;
  private @Nullable Long totalMemoryBytes;
  private @Nullable Long maxMemoryBytes;

  private @Nullable Long totalGcCount;
  private @Nullable Double totalGcTimeMs;
  private @Nullable Long totalBlockingGcCount;
  private @Nullable Double totalBlockingGcTimeMs;
  private @Nullable Long totalPreOomeGcCount;
  private @Nullable Double totalTimeWaitingForGcMs;

  public @Nullable Long getFreeMemoryBytes() {
    return freeMemoryBytes;
  }

  public void setFreeMemoryBytes(final @Nullable Long freeMemoryBytes) {
    this.freeMemoryBytes = freeMemoryBytes;
  }

  public @Nullable Long getFreeMemoryUntilGcBytes() {
    return freeMemoryUntilGcBytes;
  }

  public void setFreeMemoryUntilGcBytes(final @Nullable Long freeMemoryUntilGcBytes) {
    this.freeMemoryUntilGcBytes = freeMemoryUntilGcBytes;
  }

  public @Nullable Long getFreeMemoryUntilOOMEBytes() {
    return freeMemoryUntilOOMEBytes;
  }

  public void setFreeMemoryUntilOOMEBytes(final @Nullable Long freeMemoryUntilOOMEBytes) {
    this.freeMemoryUntilOOMEBytes = freeMemoryUntilOOMEBytes;
  }

  public @Nullable Long getTotalMemoryBytes() {
    return totalMemoryBytes;
  }

  public void setTotalMemoryBytes(final @Nullable Long totalMemoryBytes) {
    this.totalMemoryBytes = totalMemoryBytes;
  }

  public @Nullable Long getMaxMemoryBytes() {
    return maxMemoryBytes;
  }

  public void setMaxMemoryBytes(final @Nullable Long maxMemoryBytes) {
    this.maxMemoryBytes = maxMemoryBytes;
  }

  public @Nullable Long getTotalGcCount() {
    return totalGcCount;
  }

  public void setTotalGcCount(final @Nullable Long totalGcCount) {
    this.totalGcCount = totalGcCount;
  }

  public @Nullable Double getTotalGcTimeMs() {
    return totalGcTimeMs;
  }

  public void setTotalGcTimeMs(final @Nullable Double totalGcTimeMs) {
    this.totalGcTimeMs = totalGcTimeMs;
  }

  public @Nullable Long getTotalBlockingGcCount() {
    return totalBlockingGcCount;
  }

  public void setTotalBlockingGcCount(final @Nullable Long totalBlockingGcCount) {
    this.totalBlockingGcCount = totalBlockingGcCount;
  }

  public @Nullable Double getTotalBlockingGcTimeMs() {
    return totalBlockingGcTimeMs;
  }

  public void setTotalBlockingGcTimeMs(final @Nullable Double totalBlockingGcTimeMs) {
    this.totalBlockingGcTimeMs = totalBlockingGcTimeMs;
  }

  public @Nullable Long getTotalPreOomeGcCount() {
    return totalPreOomeGcCount;
  }

  public void setTotalPreOomeGcCount(final @Nullable Long totalPreOomeGcCount) {
    this.totalPreOomeGcCount = totalPreOomeGcCount;
  }

  public @Nullable Double getTotalTimeWaitingForGcMs() {
    return totalTimeWaitingForGcMs;
  }

  public void setTotalTimeWaitingForGcMs(final @Nullable Double totalTimeWaitingForGcMs) {
    this.totalTimeWaitingForGcMs = totalTimeWaitingForGcMs;
  }
}
