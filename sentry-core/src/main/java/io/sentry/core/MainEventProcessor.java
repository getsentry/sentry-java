package io.sentry.core;

import io.sentry.core.util.Objects;

public class MainEventProcessor implements EventProcessor {

  private final SentryOptions options;
  private final SentryThreadFactory sentryThreadFactory = new SentryThreadFactory();
  private final SentryStackTraceFactory sentryStackTraceFactory = new SentryStackTraceFactory();
  private final SentryExceptionFactory sentryExceptionFactory =
      new SentryExceptionFactory(sentryStackTraceFactory);

  MainEventProcessor(SentryOptions options) {
    this.options = Objects.requireNonNull(options, "The SentryOptions is required.");
  }

  @Override
  public SentryEvent process(SentryEvent event) {
    if (event.getThreads() == null) {
      event.setThreads(sentryThreadFactory.getCurrentThreads());
    }

    if (event.getRelease() == null) {
      event.setRelease(options.getRelease());
    }
    if (event.getEnvironment() == null) {
      event.setEnvironment(options.getEnvironment());
    }

    Throwable throwable = event.getThrowable();
    if (throwable != null) {
      event.setExceptions(sentryExceptionFactory.getSentryExceptions(throwable));
    }

    return event;
  }
}
