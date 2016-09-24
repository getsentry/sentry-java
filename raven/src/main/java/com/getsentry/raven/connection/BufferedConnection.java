package com.getsentry.raven.connection;

import com.getsentry.raven.Raven;
import com.getsentry.raven.buffer.Buffer;
import com.getsentry.raven.event.Event;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Connection wrapper that sends Events to an Buffer when send fails.
 */
public class BufferedConnection implements Connection {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Flusher flusher;

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

        this.flusher = new BufferedConnection.Flusher();
        scheduler.scheduleAtFixedRate(flusher, 1, 1, TimeUnit.MINUTES);
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

    class Flusher implements Runnable {
        /**
         * Use the Events iterator, always try to send 1 Event (if one exists) as a canary,
         * even if we think the connection is down. Then only keep sending events if we still
         * think the connection is up.
         */
        @Override
        public void run() {
            Iterator<Event> events = buffer.getEvents();
            while (events.hasNext()) {
                Event event = events.next();

                try {
                    send(event);
                } catch (Exception e) {
                    // Connectivity issues, give up until next Flusher run.
                    return;
                }
            }
        }
    }
}
