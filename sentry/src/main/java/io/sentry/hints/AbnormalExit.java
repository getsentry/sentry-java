package io.sentry.hints;

import org.jetbrains.annotations.Nullable;

/**
 * Marker interface for Sessions experiencing abnormal status.
 *
 * <p>This includes exits that were not classified as normal terminations or crashes, such as
 * Android ANRs and MemoryLimiter process deaths.
 *
 * <p><b>Note:</b> Some existing discriminator code (`instanceof AbnormalExit`) is shaped by the
 * historical ANR-only usage of this interface. New implementations should review all of those call
 * sites carefully to ensure ANR-specific behavior isn't applied accidentally.
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
