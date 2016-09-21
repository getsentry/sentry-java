package com.getsentry.raven.buffer;

import com.getsentry.raven.event.Event;

import java.util.Collections;
import java.util.Iterator;

public class NoopBuffer implements Buffer {
    @Override
    public void add(Event event) {

    }

    @Override
    public void discard(Event event) {

    }

    @Override
    public Iterator<Event> getEvents() {
        return Collections.emptyIterator();
    }
}
