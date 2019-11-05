package io.sentry.core;

import static io.sentry.core.ILogger.logIfNotNull;

import io.sentry.core.util.Objects;

/**
 * Sends any uncaught exception to Sentry, then passes the exception on to the pre-existing uncaught
 * exception handler.
 */
public final class UncaughtExceptionHandlerIntegration
    implements Integration, Thread.UncaughtExceptionHandler {
  /** Reference to the pre-existing uncaught exception handler. */
  private Thread.UncaughtExceptionHandler defaultExceptionHandler;

  private IHub hub;
  private SentryOptions options;

  private boolean isRegistered = false;
  private UncaughtExceptionHandler threadAdapter;

  UncaughtExceptionHandlerIntegration() {
    this(UncaughtExceptionHandler.Adapter.INSTANCE);
  }

  UncaughtExceptionHandlerIntegration(UncaughtExceptionHandler threadAdapter) {
    this.threadAdapter = Objects.requireNonNull(threadAdapter, "threadAdapter is required.");
  }

  @Override
  public void register(IHub hub, SentryOptions options) {
    if (isRegistered) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.ERROR,
          "Attempt to register a UncaughtExceptionHandlerIntegration twice. ");
      return;
    }
    isRegistered = true;

    this.hub = hub;
    this.options = options;
    Thread.UncaughtExceptionHandler currentHandler =
        threadAdapter.getDefaultUncaughtExceptionHandler();
    if (currentHandler != null) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.DEBUG,
          "default UncaughtExceptionHandler class='" + currentHandler.getClass().getName() + "'");
      defaultExceptionHandler = currentHandler;
    }

    threadAdapter.setDefaultUncaughtExceptionHandler(this);
  }

  @Override
  public void uncaughtException(Thread thread, Throwable thrown) {
    logIfNotNull(options.getLogger(), SentryLevel.INFO, "Uncaught exception received.");

    try {
      // TODO: Set Thread info to the scope?
      this.hub.captureException(thrown);
    } catch (Exception e) {
      logIfNotNull(
          options.getLogger(), SentryLevel.ERROR, "Error sending uncaught exception to Sentry.", e);
    }

    if (defaultExceptionHandler != null) {
      defaultExceptionHandler.uncaughtException(thread, thrown);
    }
  }
}
