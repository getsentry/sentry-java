package io.sentry.core.transport;

import io.sentry.core.SentryEvent;

/**
 * Implementations of this interface are used as a kind of persistent storage for events that wait
 * to be sent to the Sentry server.
 *
 * <p>Note that this interface doesn't handle the situation of resending the stored events after a
 * crash. While that is surely one of the main usecases for the persistent storage of events, the
 * re-initialization is out of scope of the event transport logic.
 */
public interface IEventCache {

  /**
   * Stores the event so that it can be sent later.
   *
   * @param event the event to store
   */
  void store(SentryEvent event);

  /**
   * Discards the event from the storage. This means that the event has been successfully sent. Note
   * that this MUST NOT fail on events that haven't been stored before (i.e. this method is called
   * even for events that has been sent on the first attempt).
   *
   * @param event the event to discard from storage
   */
  void discard(SentryEvent event);
}
