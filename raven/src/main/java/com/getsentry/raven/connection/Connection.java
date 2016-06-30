package com.getsentry.raven.connection;

import com.getsentry.raven.event.Event;

import java.io.Closeable;

/**
 * Connection to a Sentry server, allowing to send captured events.
 */
public interface Connection extends Closeable {
    /**
     * Sends an event to the Sentry server.
     *
     * @param event captured event to add in Sentry.
     */
    void send(Event event);

    /**
     * Add a callback that is called when an exception occurs while attempting to
     * send events to the Sentry server.
     *
     * @param eventSendFailureCallback callback instance
     */
    void addEventSendFailureCallback(EventSendFailureCallback eventSendFailureCallback);

}
