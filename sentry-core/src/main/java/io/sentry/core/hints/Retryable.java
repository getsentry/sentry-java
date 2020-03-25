package io.sentry.core.hints;

public interface Retryable {
  boolean isRetry();

  void setRetry(boolean retry);
}
