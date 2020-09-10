package io.sentry.hints;

public interface Retryable {
  boolean isRetry();

  void setRetry(boolean retry);
}
