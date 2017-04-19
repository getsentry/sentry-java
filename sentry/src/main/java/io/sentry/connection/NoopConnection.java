package io.sentry.connection;

import io.sentry.event.Event;

import java.io.IOException;

/**
 * Connection that drops events.
 *
 * Only use it when no DSN is set.
 */
public class NoopConnection extends AbstractConnection {
    /**
     * Creates a connection that drops events.
     */
    public NoopConnection() {
        super(null, null);
    }

    @Override
    protected void doSend(Event event) throws ConnectionException {
    }

    @Override
    public void close() throws IOException {
    }
}
