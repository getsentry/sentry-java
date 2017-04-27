package io.sentry.connection;

import io.sentry.event.Event;

import java.io.Closeable;

/**
 * Connection to a Sentry server, allowing to send captured events.
 */
public interface Connection extends Closeable {
    /**
     * Sends an event to the Sentry server.
     *
     * @param event captured event to add in Sentry.
     * @throws ConnectionException Thrown when an Event send fails.
     */
    void send(Event event) throws ConnectionException;

    /**
     * Add a callback that is called when an exception occurs while attempting to
     * send events to the Sentry server.
     *
     * @param eventSendCallback callback instance
     */
    void addEventSendCallback(EventSendCallback eventSendCallback);

}
