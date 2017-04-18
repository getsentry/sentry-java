package io.sentry.buffer;

import io.sentry.event.Event;

import java.util.Iterator;

/**
 * Buffer that is called by a {@link io.sentry.connection.BufferedConnection} when an {@link Event} send
 * fails with a {@link io.sentry.connection.ConnectionException}.
 */
public interface Buffer {
    /**
     * Buffer the {@link Event} so that it can be flushed to the Sentry server at a later
     * point in time.
     *
     * @param event Event object that should be buffered.
     */
    void add(Event event);

    /**
     * Discard and {@link Event} from the buffer. Note: the {@link Event} may or may not exist in
     * the buffer.
     *
     * @param event Event to discard from the buffer.
     */
    void discard(Event event);

    /**
     * Returns an Iterator of {@link Event}s in the buffer.
     *
     * @return Iterator of Events in the buffer.
     */
    Iterator<Event> getEvents();
}
