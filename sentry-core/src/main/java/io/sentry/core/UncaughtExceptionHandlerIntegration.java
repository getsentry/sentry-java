package io.sentry.core;

import static io.sentry.core.ILogger.logIfNotNull;

import io.sentry.core.exception.ExceptionMechanismThrowable;
import io.sentry.core.protocol.Mechanism;
import io.sentry.core.util.Objects;
import java.io.Closeable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Sends any uncaught exception to Sentry, then passes the exception on to the pre-existing uncaught
 * exception handler.
 */
public final class UncaughtExceptionHandlerIntegration
    implements Integration, Thread.UncaughtExceptionHandler, Closeable {
  /** Reference to the pre-existing uncaught exception handler. */
  private Thread.UncaughtExceptionHandler defaultExceptionHandler;

  private IHub hub;
  private SentryOptions options;

  private boolean isRegistered = false;
  private UncaughtExceptionHandler threadAdapter;

  UncaughtExceptionHandlerIntegration() {
    this(UncaughtExceptionHandler.Adapter.getInstance());
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
      Throwable throwable = getUnhandledThrowable(thread, thrown);
      // SDK is expected to write to disk synchronously events that crash the process
      this.hub.captureException(throwable);
    } catch (Exception e) {
      logIfNotNull(
          options.getLogger(), SentryLevel.ERROR, "Error sending uncaught exception to Sentry.", e);
    }

    if (defaultExceptionHandler != null) {
      defaultExceptionHandler.uncaughtException(thread, thrown);
    }
  }

  @NotNull
  @TestOnly
  static Throwable getUnhandledThrowable(Thread thread, Throwable thrown) {
    Mechanism mechanism = new Mechanism();
    mechanism.setHandled(false);
    mechanism.setType("UncaughtExceptionHandler");
    return new ExceptionMechanismThrowable(mechanism, thrown, thread);
  }

  @Override
  public void close() {
    if (defaultExceptionHandler != null
        && this == threadAdapter.getDefaultUncaughtExceptionHandler()) {
      threadAdapter.setDefaultUncaughtExceptionHandler(defaultExceptionHandler);
    }
  }
}
