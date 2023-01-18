package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Used for collecting data about cpu load when a transaction is active. */
@ApiStatus.Internal
public interface ICollector {

  void setup();

  /** Used for collecting data about cpu load when a transaction is active. */
  void collect(@NotNull final Iterable<PerformanceCollectionData> performanceCollectionData);
}
