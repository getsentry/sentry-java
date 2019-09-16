package io.sentry;

public interface EventProcessor {
  public SentryEvent Process(SentryEvent event);
}
