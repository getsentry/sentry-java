package io.sentry.hints;

/** Marker interface for a reusable Hint */
public interface Resettable {
  /** Reset the Hint to its initial state */
  void reset();
}
