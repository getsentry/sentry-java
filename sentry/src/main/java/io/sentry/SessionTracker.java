package io.sentry;

/** Tracks users' sessions. */
interface SessionTracker {
  /** Should be called when session starts. */
  void startSession();

  /** Should be called when session ends. */
  void endSession();
}
