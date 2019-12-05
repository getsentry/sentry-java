package io.sentry.core;

import static io.sentry.core.ILogger.logIfNotNull;

import io.sentry.core.hints.Cached;
import io.sentry.core.protocol.SentryException;
import io.sentry.core.util.Objects;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MainEventProcessor implements EventProcessor {

  private final SentryOptions options;
  private final SentryThreadFactory sentryThreadFactory;
  private final SentryExceptionFactory sentryExceptionFactory;

  MainEventProcessor(final SentryOptions options) {
    this.options = Objects.requireNonNull(options, "The SentryOptions is required.");

    SentryStackTraceFactory sentryStackTraceFactory =
        new SentryStackTraceFactory(options.getInAppExcludes(), options.getInAppIncludes());

    sentryExceptionFactory = new SentryExceptionFactory(sentryStackTraceFactory);
    sentryThreadFactory = new SentryThreadFactory(sentryStackTraceFactory);
  }

  MainEventProcessor(
      final SentryOptions options,
      final SentryThreadFactory sentryThreadFactory,
      final SentryExceptionFactory sentryExceptionFactory) {
    this.options = Objects.requireNonNull(options, "The SentryOptions is required.");
    this.sentryThreadFactory =
        Objects.requireNonNull(sentryThreadFactory, "The SentryThreadFactory is required.");
    this.sentryExceptionFactory =
        Objects.requireNonNull(sentryExceptionFactory, "The SentryExceptionFactory is required.");
  }

  @Override
  public SentryEvent process(SentryEvent event, @Nullable Object hint) {
    if (event.getRelease() == null) {
      event.setRelease(options.getRelease());
    }
    if (event.getEnvironment() == null) {
      event.setEnvironment(options.getEnvironment());
    }

    if (event.getPlatform() == null) {
      // this actually means JVM related.
      event.setPlatform("java");
    }

    Throwable throwable = event.getThrowable();
    if (throwable != null) {
      event.setExceptions(sentryExceptionFactory.getSentryExceptions(throwable));
    }

    if (event.getThreads() == null) {
      if (!(hint instanceof Cached)) {
        Long crashedThreadId = null;
        List<SentryException> exceptions = event.getExceptions();
        if (event.getExceptions() != null && !exceptions.isEmpty()) {
          for (SentryException exception : exceptions) {
            if (exception != null
                && exception.getMechanism() != null
                // If mechanism is set to handled=false, this will crash the app.
                // Provide the thread-id if available to mark the thread-list with the crashed one.
                && Boolean.FALSE.equals(exception.getMechanism().isHandled())) {
              crashedThreadId = exception.getThreadId();
              break;
            }
          }
        }
        event.setThreads(sentryThreadFactory.getCurrentThreads(crashedThreadId));
      } else {
        logIfNotNull(
            options.getLogger(),
            SentryLevel.DEBUG,
            "Event was cached so not applying threads: %s",
            event.getEventId());
      }
    }

    return event;
  }
}
