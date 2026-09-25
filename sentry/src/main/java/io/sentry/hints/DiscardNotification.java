package io.sentry.hints;

import org.jetbrains.annotations.ApiStatus;

/** Notified when an envelope is dropped before it is sent or stored. */
@ApiStatus.Internal
public interface DiscardNotification {
  void markDiscarded();
}
