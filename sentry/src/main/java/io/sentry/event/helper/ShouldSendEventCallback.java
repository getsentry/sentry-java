package io.sentry.event.helper;

import io.sentry.event.Event;

/**
 * Callback that decides whether or not an {@link Event} should be sent to Sentry.
 */
public interface ShouldSendEventCallback {

    /**
     * Callback that decides whether or not an {@link Event} should be sent to Sentry.
     *
     * @param event {@link Event} that may be sent.
     * @return true if the {@link Event} should be sent, false otherwise.
     */
    boolean shouldSend(Event event);
}
