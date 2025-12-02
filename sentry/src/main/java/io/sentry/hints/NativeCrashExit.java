package io.sentry.hints;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * This is not sensationally useful right now. It only exists as marker interface to distinguish Tombstone events from AbnormalExits, which
 * they are not. The timestamp is used to record the timestamp of the last reported native crash we retrieved from the ApplicationExitInfo.
 */
@ApiStatus.Internal
public interface NativeCrashExit {
  /** When exactly the crash exit happened */
  @Nullable
  Long timestamp();
}
