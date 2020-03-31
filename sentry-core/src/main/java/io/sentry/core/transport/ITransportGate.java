package io.sentry.core.transport;

/**
 * Implementations of this interface serve as gatekeepers that allow or disallow sending of the
 * events through the {@link AsyncConnection}. For example, it is unnecessary to send the events if
 * the device is offline.
 *
 * <p>Events are stored in a cache until sending is allowed again (or maximum number of retries to
 * send an event has been reached).
 */
public interface ITransportGate {

  /** @return true if it is possible to send events to the Sentry server, false otherwise */
  boolean isConnected();
}
