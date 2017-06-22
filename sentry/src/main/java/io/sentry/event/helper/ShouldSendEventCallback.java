package io.sentry.event.helper;

import io.sentry.event.Event;

/**
 * Callback for preventing an event from being sent to sentry.
 */
public interface ShouldSendEventCallback {

    /**
     * Called before an event will be sent to the server.
     *
     * @param event Event that was sent
     * @return boolen return false to prevent the event from being sent
     */
    boolean shouldSend(Event event);
}
