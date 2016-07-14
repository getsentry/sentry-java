package com.getsentry.raven.connection;

import com.getsentry.raven.event.Event;

/**
 * Callback that is called when an exception occurs while attempting to
 * send events to the Sentry server.
 */
public interface EventSendFailureCallback {

    /**
     * Called when an exception occurs while attempting to
     * send events to the Sentry server.
     *
     * @param event Event that couldn't be sent
     * @param exception Exception that occurred while attempting to send the Event
     */
    void onFailure(Event event, Exception exception);

}
