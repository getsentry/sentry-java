package io.sentry.hints;

import org.jetbrains.annotations.ApiStatus;

/** Marker interface for envelopes to notify when they are dropped without being sent or stored */
@ApiStatus.Internal
public interface DiscardNotification {
  void markDiscarded();
}
