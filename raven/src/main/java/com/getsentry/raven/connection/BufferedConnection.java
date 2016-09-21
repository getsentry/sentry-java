package com.getsentry.raven.connection;

import com.getsentry.raven.buffer.Buffer;
import com.getsentry.raven.event.Event;

import java.io.IOException;

/**
 * Connection wrapper that sends Events to an Buffer when send fails.
 */
public class BufferedConnection implements Connection {
    private Connection actualConnection;
    private Buffer buffer;

    /**
     * Construct a BufferedConnection with a {@link Connection} to wrap and an {@link Buffer}.
     *
     * @param actualConnection Connection to wrap.
     * @param buffer Buffer to be used when {@link Connection#send(Event)}s fail.
     */
    public BufferedConnection(Connection actualConnection, Buffer buffer) {
        this.actualConnection = actualConnection;
        this.buffer = buffer;
    }

    @Override
    public void send(Event event) {
        try {
            actualConnection.send(event);
            buffer.discard(event);
        } catch (Exception e) {
            buffer.add(event);
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
