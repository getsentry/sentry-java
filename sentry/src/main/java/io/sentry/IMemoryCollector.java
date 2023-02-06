package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Used for collecting data about memory load when a transaction is active. */
@ApiStatus.Internal
public interface IMemoryCollector {
  /** Used for collecting data about memory load when a transaction is active. */
  @Nullable
  MemoryCollectionData collect();
}
