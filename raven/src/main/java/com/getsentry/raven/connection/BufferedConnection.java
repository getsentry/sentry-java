package com.getsentry.raven.connection;

import com.getsentry.raven.buffer.EventBuffer;
import com.getsentry.raven.event.Event;

import java.io.IOException;

/**
 * Connection wrapper that sends Events to an EventBuffer when send fails.
 */
public class BufferedConnection implements Connection {
    private Connection actualConnection;
    private EventBuffer eventBuffer;

    /**
     * Construct a BufferedConnection with a {@link Connection} to wrap and an {@link EventBuffer}.
     *
     * @param actualConnection Connection to wrap.
     * @param eventBuffer EventBuffer to be used when {@link Connection#send(Event)}s fail.
     */
    public BufferedConnection(Connection actualConnection, EventBuffer eventBuffer) {
        this.actualConnection = actualConnection;
        this.eventBuffer = eventBuffer;
    }

    @Override
    public void send(Event event) {
        try {
            actualConnection.send(event);
        } catch (Exception e) {
            eventBuffer.buffer(event);
            throw e;
        }
    }

    @Override
    public void addEventSendFailureCallback(EventSendFailureCallback eventSendFailureCallback) {
        actualConnection.addEventSendFailureCallback(eventSendFailureCallback);
    }

    @Override
    public void close() throws IOException {
        actualConnection.close();
    }
}
