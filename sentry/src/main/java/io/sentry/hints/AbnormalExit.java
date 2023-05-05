package io.sentry.hints;

import org.jetbrains.annotations.Nullable;

/** Marker interface for Sessions experiencing abnormal status */
public interface AbnormalExit {

  /** What was the mechanism this Session has abnormal'ed with */
  @Nullable
  String mechanism();

  /** Whether the current thread should be ignored from being marked as crashed, e.g. a watchdog */
  default boolean ignoreCurrentThread() {
    return false;
  }

  /** When exactly the abnormal exit happened */
  @Nullable
  default Long timestamp() {
    return null;
  }
}
