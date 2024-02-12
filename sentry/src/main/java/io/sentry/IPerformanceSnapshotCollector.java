package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Used for collecting data about vitals (memory, cpu, etc.) when a transaction is active. */
@ApiStatus.Internal
public interface IPerformanceSnapshotCollector extends IPerformanceCollector {

  void setup();

  void collect(final @NotNull PerformanceCollectionData performanceCollectionData);
}
