package io.sentry.connection;

import io.sentry.event.Event;

/**
 * Callback that is called upon success or failure while attempting to
 * send events to the Sentry server.
 */
public interface EventSendCallback {

    /**
     * Called when an exception occurs while attempting to
     * send events to the Sentry server.
     *
     * @param event Event that couldn't be sent
     * @param exception Exception that occurred while attempting to send the Event
     */
    void onFailure(Event event, Exception exception);

    /**
     * Called when an event is successfully sent to the Sentry server.
     *
     * @param event Event that was sent
     */
    void onSuccess(Event event);
}
