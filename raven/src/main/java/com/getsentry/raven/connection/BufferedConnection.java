package com.getsentry.raven.connection;

import com.getsentry.raven.buffer.Buffer;
import com.getsentry.raven.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Connection wrapper that sends Events to an Buffer when send fails.
 */
public class BufferedConnection implements Connection {

    private static final Logger logger = LoggerFactory.getLogger(BufferedConnection.class);

    /**
     * Flusher ExecutorService, created in a verbose way so that it doesn't keep
     * the JVM running after main() exits.
     */
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            }
        });

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
        scheduler.scheduleAtFixedRate(flusher, 0, 1, TimeUnit.MINUTES);
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

    private class Flusher implements Runnable {
        @Override
        public void run() {
            logger.debug("Running Flusher");

            Iterator<Event> events = buffer.getEvents();
            while (events.hasNext()) {
                Event event = events.next();

                try {
                    logger.debug("Flusher attempting to send Event: " + event.getId());
                    send(event);
                    logger.debug("Flusher successfully sent Event: " + event.getId());
                } catch (Exception e) {
                    logger.debug("Flusher failed to send Event: " + event.getId(), e);

                    // Connectivity issues, give up until next Flusher run.
                    return;
                }
            }
        }
    }
}
