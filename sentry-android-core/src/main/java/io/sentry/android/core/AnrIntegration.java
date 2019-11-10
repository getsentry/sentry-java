package io.sentry.android.core;

import static io.sentry.core.ILogger.logIfNotNull;

import io.sentry.core.IHub;
import io.sentry.core.Integration;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.exception.ExceptionMechanismException;
import io.sentry.core.protocol.Mechanism;

public final class AnrIntegration implements Integration {
  private static ANRWatchDog anrWatchDog;

  @Override
  public void register(IHub hub, SentryOptions options) {
    register(hub, (SentryAndroidOptions) options);
  }

  private void register(IHub hub, SentryAndroidOptions options) {
    logIfNotNull(options.getLogger(), SentryLevel.DEBUG, "ANR enabled: %s", options.isAnrEnabled());

    if (options.isAnrEnabled() && anrWatchDog == null) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.DEBUG,
          "ANR timeout in milliseconds: %d",
          options.getAnrTimeoutIntervalMills());

      anrWatchDog =
          new ANRWatchDog(
              options.getAnrTimeoutIntervalMills(),
              options.isAnrReportInDebug(),
              error -> {
                logIfNotNull(
                    options.getLogger(),
                    SentryLevel.INFO,
                    "ANR triggered with message: %s",
                    error.getMessage());

                Mechanism mechanism = new Mechanism();
                mechanism.setType("ANR");
                mechanism.setHandled(false);
                ExceptionMechanismException throwable =
                    new ExceptionMechanismException(mechanism, error, Thread.currentThread());

                hub.captureException(throwable);
              },
              options.getLogger());
      anrWatchDog.start();
    }
  }
}
