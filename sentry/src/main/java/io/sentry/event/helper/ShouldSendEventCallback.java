package io.sentry.event.helper;

import io.sentry.event.Event;

public interface ShouldSendEventCallback {

    /**
     * Called before an event will be sent to the server,
     *
     * @param event Event that was sent
     * @return boolen return false to prevent the event from being sent
     */
    boolean shouldSend(Event event);
}
