package io.sentry.hints;

import org.jetbrains.annotations.Nullable;

/**
 * Marker interface for Sessions experiencing abnormal status.
 *
 * <p>This includes exits that were not classified as normal terminations or crashes, such as
 * Android ANRs and MemoryLimiter process deaths.
 */
public interface AbnormalExit {

  /** What was the mechanism this Session has abnormal'ed with */
  @Nullable
  String mechanism();

  /** Whether the current thread should be ignored from being marked as crashed, e.g. a watchdog */
  boolean ignoreCurrentThread();

  /** When exactly the abnormal exit happened */
  @Nullable
  Long timestamp();
}
