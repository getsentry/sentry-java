package com.getsentry.raven.buffer;

import com.getsentry.raven.event.Event;

/**
 * EventBuffer that is called by a {@link com.getsentry.raven.connection.BufferedConnection} when an {@link Event} send
 * fails with a {@link com.getsentry.raven.connection.ConnectionException}.
 */
public interface EventBuffer {
    /**
     * Implementations are expected to buffer the {@link Event} somewhere so that they can be
     * {@link EventBuffer#flush()}ed at a later point in time.
     *
     * @param event Event object that should be buffered.
     */
    void buffer(Event event);

    /**
     * Implementation are expected to flush their buffers to the Sentry server.
     */
    void flush();
}
