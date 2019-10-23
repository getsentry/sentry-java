package io.sentry.core;

public interface Integration {
  void register(IHub hub, SentryOptions options);
}
