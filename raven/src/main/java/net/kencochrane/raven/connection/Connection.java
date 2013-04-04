package net.kencochrane.raven.connection;

import net.kencochrane.raven.event.LoggedEvent;

/**
 * Connection to a Sentry server, allowing to send captured events.
 */
public interface Connection {
    /**
     * Sends an event to the sentry server.
     *
     * @param event captured event to add in Sentry.
     */
    void send(LoggedEvent event);
}
