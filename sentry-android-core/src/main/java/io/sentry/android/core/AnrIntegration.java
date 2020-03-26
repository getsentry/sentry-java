package io.sentry.android.core;

import io.sentry.core.IHub;
import io.sentry.core.ILogger;
import io.sentry.core.Integration;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.exception.ExceptionMechanismException;
import io.sentry.core.protocol.Mechanism;
import io.sentry.core.util.Objects;
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

  private static ANRWatchDog anrWatchDog;
  private @Nullable SentryOptions options;

  @Override
  public final void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "SentryOptions is required");
    register(hub, (SentryAndroidOptions) options);
  }

  private void register(final @NotNull IHub hub, final @NotNull SentryAndroidOptions options) {
    options
        .getLogger()
        .log(SentryLevel.DEBUG, "AnrIntegration enabled: %s", options.isAnrEnabled());

    if (options.isAnrEnabled() && anrWatchDog == null) {
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
              options.getLogger());
      anrWatchDog.start();

      options.getLogger().log(SentryLevel.DEBUG, "AnrIntegration installed.");
    }
  }

  @TestOnly
  void reportANR(IHub hub, final @NotNull ILogger logger, ApplicationNotResponding error) {
    logger.log(SentryLevel.INFO, "ANR triggered with message: %s", error.getMessage());

    Mechanism mechanism = new Mechanism();
    mechanism.setType("ANR");
    ExceptionMechanismException throwable =
        new ExceptionMechanismException(mechanism, error, error.getThread());

    hub.captureException(throwable);
  }

  @TestOnly
  ANRWatchDog getANRWatchDog() {
    return anrWatchDog;
  }

  @Override
  public void close() throws IOException {
    if (anrWatchDog != null) {
      anrWatchDog.interrupt();
      anrWatchDog = null;
      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "AnrIntegration removed.");
      }
    }
  }
}
