package io.sentry.hints;

import org.jetbrains.annotations.Nullable;

/**
 * Marker interface for Sessions experiencing abnormal status.
 *
 * <p>Includes exits that were not classified as normal terminations or crashes, such as Android
 * ANRs and MemoryLimiter process deaths.
 *
 * <p><b>Note:</b> Some existing discriminator code (`instanceof AbnormalExit`) is shaped by the
 * historical ANR-only usage of this interface. New implementations should review all of those call
 * sites carefully to ensure ANR-specific behavior isn't applied accidentally.
 */
public interface AbnormalExit {

  /** What was the mechanism this Session has abnormal'ed with */
  @Nullable
  String mechanism();

  /**
   * Whether the current thread (e.g., a watchdog) should be ignored by the `MainEventProcessor`
   * when deciding which threads from the current process should be bound to the Sentry event
   * associated with this `AbnormalExit`.
   *
   * <p>This method effectively no-ops for types implementing both {@link AbnormalExit} and {@link
   * Backfillable}, as implementors of `Backfillable` are not sent to the `MainEventProcessor`.
   */
  boolean ignoreCurrentThread();

  /**
   * When exactly the abnormal exit happened.
   *
   * <p>Epoch time in milliseconds, or null.
   */
  @Nullable
  Long timestamp();
}
