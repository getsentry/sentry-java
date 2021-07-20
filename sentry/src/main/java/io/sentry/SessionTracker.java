package io.sentry;

interface SessionTracker {
  void startSession();

  void endSession();
}
