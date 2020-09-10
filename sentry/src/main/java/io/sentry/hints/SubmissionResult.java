package io.sentry.hints;

public interface SubmissionResult {
  void setResult(boolean success);

  boolean isSuccess();
}
