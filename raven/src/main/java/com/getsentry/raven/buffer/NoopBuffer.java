package com.getsentry.raven.buffer;

import com.getsentry.raven.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;

/**
 * A Noop {@link Buffer} instance, used if a "real" implementation can't be properly
 * constructed.
 */
public class NoopBuffer implements Buffer {

    private static final Logger logger = LoggerFactory.getLogger(NoopBuffer.class);

    /**
     * Noop add: the {@link Event} is dropped.
     *
     * @param event Event object that should be buffered.
     */
    @Override
    public void add(Event event) {
        logger.debug("Noop buffer add of Event: " + event.getId().toString());
    }

    /**
     * Noop discard.
     *
     * @param event Event to discard from the buffer.
     */
    @Override
    public void discard(Event event) {
        logger.debug("Noop buffer discard of Event: " + event.getId().toString());
    }

    /**
     * Returns a Noop (empty) Iterator of Events.
     *
     * @return Empty Iterator of Events
     */
    @Override
    public Iterator<Event> getEvents() {
        return Collections.emptyIterator();
    }
}
