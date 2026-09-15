package io.sentry.hints;

import org.jetbrains.annotations.Nullable;

/**
 * Marker interface for Sessions experiencing abnormal status.
 *
 * <p><b>Note:</b> While this interface applies to the broad category of abnormal exits (meaning any
 * exits that weren't classified as normal terminations or crashes) it currently is exclusively used
 * as a hint marker for Android ANRs (both watchdog and ApplicationExitInfo based). If additional
 * categories of abnormal exits were introduced, all instances of discriminator code (`instanceof
 * AbnormalExit`) should be carefully reviewed for ANR specifics accidentally being applied.
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
