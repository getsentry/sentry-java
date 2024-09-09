package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Used for performing operations when a transaction is started or ended. */
@ApiStatus.Internal
public interface IContinuousProfiler {
  boolean isRunning();

  void start();

  void stop();

  void setHub(final @NotNull IHub hub);

  /** Cancel the profiler and stops it. Used on SDK close. */
  void close();
}
