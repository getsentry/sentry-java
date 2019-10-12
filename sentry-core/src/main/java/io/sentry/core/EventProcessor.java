package io.sentry.core;

public interface EventProcessor {
  SentryEvent process(SentryEvent event);
}
