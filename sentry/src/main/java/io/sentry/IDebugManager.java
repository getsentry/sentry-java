package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface IDebugManager {

  /**
   * Returns whether a debugger is currently attached or the process is waiting for a debugger to
   * attach.
   */
  boolean isDebuggerConnected();
}
