package io.sentry.core.hints;

public interface RetryableHint {
  boolean getRetry();

  void setRetry(boolean retry);
}
