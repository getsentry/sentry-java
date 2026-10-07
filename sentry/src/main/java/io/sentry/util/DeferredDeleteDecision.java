package io.sentry.util;

import io.sentry.ILogger;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The delete-or-keep decision for a cached file whose send is still in flight, shared by the hints
 * that implement {@link io.sentry.hints.DeferredDelete}.
 *
 * <p>Exactly one actor applies the decision: the caller, when the outcome of the send arrived
 * before it stopped waiting, or this class, from the transport thread, when it did not.
 */
@ApiStatus.Internal
public final class DeferredDeleteDecision {

  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  private final @NotNull ILogger logger;
  private @Nullable File deferredFile;
  private boolean outcomeKnown;

  public DeferredDeleteDecision(final @NotNull ILogger logger) {
    this.logger = logger;
  }

  /**
   * Hands a file over to this decision.
   *
   * @param file the cached file
   * @return true if this decision took over, false if the outcome is already known and the caller
   *     keeps the decision
   */
  public boolean defer(final @NotNull File file) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (outcomeKnown) {
        return false;
      }
      deferredFile = file;
      return true;
    }
  }

  /**
   * Reports the outcome of the send, and applies a deferred decision if one is pending.
   *
   * @param retry whether the envelope is to be sent again later
   */
  public void onOutcomeKnown(final boolean retry) {
    final @Nullable File file;
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      outcomeKnown = true;
      file = deferredFile;
      deferredFile = null;
    }

    if (file == null) {
      return;
    }
    if (retry) {
      logger.log(
          SentryLevel.INFO, "File not deleted since retry was marked. %s.", file.getAbsolutePath());
    } else {
      FileUtils.deleteFile(file, logger);
    }
  }
}
