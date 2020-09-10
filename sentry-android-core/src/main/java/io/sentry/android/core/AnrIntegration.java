package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.content.Context;
import io.sentry.IHub;
import io.sentry.ILogger;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * When the UI thread of an Android app is blocked for too long, an "Application Not Responding"
 * (ANR) error is triggered. Sends an event if an ANR happens
 */
public final class AnrIntegration implements Integration, Closeable {

  private final @NotNull Context context;

  public AnrIntegration(final @NotNull Context context) {
    this.context = context;
  }

  /**
   * Responsible for watching UI thread. being static to avoid multiple instances running at the
   * same time.
   */
  @SuppressLint("StaticFieldLeak") // we have watchDogLock to avoid those leaks
  private static @Nullable ANRWatchDog anrWatchDog;

  private @Nullable SentryOptions options;

  private static final @NotNull Object watchDogLock = new Object();

  @Override
  public final void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "SentryOptions is required");
    register(hub, (SentryAndroidOptions) options);
  }

  private void register(final @NotNull IHub hub, final @NotNull SentryAndroidOptions options) {
    options
        .getLogger()
        .log(SentryLevel.DEBUG, "AnrIntegration enabled: %s", options.isAnrEnabled());

    if (options.isAnrEnabled()) {
      synchronized (watchDogLock) {
        if (anrWatchDog == null) {
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "ANR timeout in milliseconds: %d",
                  options.getAnrTimeoutIntervalMillis());

          anrWatchDog =
              new ANRWatchDog(
                  options.getAnrTimeoutIntervalMillis(),
                  options.isAnrReportInDebug(),
                  error -> reportANR(hub, options.getLogger(), error),
                  options.getLogger(),
                  context);
          anrWatchDog.start();

          options.getLogger().log(SentryLevel.DEBUG, "AnrIntegration installed.");
        }
      }
    }
  }

  @TestOnly
  void reportANR(
      final @NotNull IHub hub,
      final @NotNull ILogger logger,
      final @NotNull ApplicationNotResponding error) {
    logger.log(SentryLevel.INFO, "ANR triggered with message: %s", error.getMessage());

    Mechanism mechanism = new Mechanism();
    mechanism.setType("ANR");
    ExceptionMechanismException throwable =
        new ExceptionMechanismException(mechanism, error, error.getThread());

    hub.captureException(throwable);
  }

  @TestOnly
  @Nullable
  ANRWatchDog getANRWatchDog() {
    return anrWatchDog;
  }

  @Override
  public void close() throws IOException {
    synchronized (watchDogLock) {
      if (anrWatchDog != null) {
        anrWatchDog.interrupt();
        anrWatchDog = null;
        if (options != null) {
          options.getLogger().log(SentryLevel.DEBUG, "AnrIntegration removed.");
        }
      }
    }
  }
}
