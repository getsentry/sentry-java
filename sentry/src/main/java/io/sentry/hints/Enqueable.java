package io.sentry.hints;

/** Marker interface for envelopes to notify when they are submitted to the http transport queue */
public interface Enqueable {
  void markEnqueued();
}
