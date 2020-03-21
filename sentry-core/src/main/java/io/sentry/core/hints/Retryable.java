package io.sentry.core.hints;

public interface Retryable {
  boolean getRetry();

  void setRetry(boolean retry);
}
