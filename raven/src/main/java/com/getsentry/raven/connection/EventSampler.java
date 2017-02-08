package com.getsentry.raven.connection;

import com.getsentry.raven.event.Event;

/**
 * Used by {@link HttpConnection} to decide whether a specific event should actually be
 * send to the server or not.
 */
public interface EventSampler {
    /**
     * Decides whether a specific event should be sent to the server or not.
     *
     * @param event Event to be checked against the sampling logic.
     * @return True if the event should be sent to the server, else False.
     */
    boolean shouldSendEvent(Event event);
}
