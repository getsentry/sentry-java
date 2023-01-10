package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Used for collecting data about cpu load when a transaction is active. */
@ApiStatus.Internal
public interface ICpuCollector {

  void setup();

  /** Used for collecting data about cpu load when a transaction is active. */
  @Nullable
  CpuCollectionData collect();
}
