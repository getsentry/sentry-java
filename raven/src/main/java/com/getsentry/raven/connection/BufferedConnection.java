package com.getsentry.raven.connection;

import com.getsentry.raven.buffer.Buffer;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
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
     * Shutdown hook used to stop the buffered connection properly when the JVM quits.
     */
    private final BufferedConnection.ShutDownHook shutDownHook = new BufferedConnection.ShutDownHook();
    /**
     * Flusher ExecutorService, created in a verbose way to use daemon threads
     * so that it doesn't keep the JVM running after main() exits.
     */
    private final ScheduledExecutorService executorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            }
        });
    /**
     * Connection used to actually send the events.
     */
    private Connection actualConnection;
    /**
     * Buffer used to store and retrieve events from.
     */
    private Buffer buffer;
    /**
     * Boolean that represents if graceful shutdown is enabled.
     */
    private boolean gracefulShutdown;
    /**
     * Timeout of the {@link #executorService}, in milliseconds.
     */
    private long shutdownTimeout;
    /**
     * Boolean used to check whether the connection is still open or not.
     */
    private volatile boolean closed = false;

    /**
     * Construct a BufferedConnection that will store events that failed to send to the provided
     * {@link Buffer} and attempt to flush them to the underlying connection later.
     *
     * @param actualConnection Connection to wrap.
     * @param buffer Buffer to be used when {@link Connection#send(Event)}s fail.
     * @param flushtime Time to wait between flush attempts, in milliseconds.
     * @param gracefulShutdown Indicates whether or not the shutdown operation should be managed by a ShutdownHook.
     * @param shutdownTimeout Timeout for graceful shutdown of the executor, in milliseconds.
     */
    public BufferedConnection(Connection actualConnection, Buffer buffer, long flushtime, boolean gracefulShutdown,
        long shutdownTimeout) {

        this.actualConnection = actualConnection;
        this.buffer = buffer;
        this.gracefulShutdown = gracefulShutdown;
        this.shutdownTimeout = shutdownTimeout;

        if (gracefulShutdown) {
            Runtime.getRuntime().addShutdownHook(shutDownHook);
        }

        Flusher flusher = new BufferedConnection.Flusher(flushtime);
        executorService.scheduleWithFixedDelay(flusher, flushtime, flushtime, TimeUnit.MILLISECONDS);
    }

    @Override
    public void send(Event event) {
        actualConnection.send(event);

        // success, remove the event from the buffer
        buffer.discard(event);
    }

    @Override
    public void addEventSendFailureCallback(EventSendFailureCallback eventSendFailureCallback) {
        actualConnection.addEventSendFailureCallback(eventSendFailureCallback);
    }

    @Override
    @SuppressWarnings("checkstyle:magicnumber")
    public void close() throws IOException {
        if (gracefulShutdown) {
            shutDownHook.enabled = false;
        }

        closed = true;
        executorService.shutdown();
        try {
            if (shutdownTimeout == -1L) {
                // Block until the executor terminates, but log periodically.
                long waitBetweenLoggingMs = 5000L;
                while (true) {
                    if (executorService.awaitTermination(waitBetweenLoggingMs, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                    logger.info("Still waiting on buffer flusher executor to terminate.");
                }
            } else if (!executorService.awaitTermination(shutdownTimeout, TimeUnit.MILLISECONDS)) {
                logger.warn("Graceful shutdown took too much time, forcing the shutdown.");
                List<Runnable> tasks = executorService.shutdownNow();
                logger.info("{} tasks failed to execute before the shutdown.", tasks.size());
            }
            logger.info("Shutdown finished.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Graceful shutdown interrupted, forcing the shutdown.");
            List<Runnable> tasks = executorService.shutdownNow();
            logger.info("{} tasks failed to execute before the shutdown.", tasks.size());
        } finally {
            actualConnection.close();
        }
    }

    /**
     * Wrap a connection so that {@link Event}s are buffered before being passed on to
     * the underlying connection.
     *
     * This is important to ensure buffering happens synchronously with {@link Event} creation,
     * before they are passed on to the (optional) asynchronous connection so that Events will
     * be stored even if the application is about to exit due to a crash.
     *
     * @param connectionToWrap {@link Connection} to wrap with buffering logic.
     * @return Connection that will write {@link Event}s to a buffer before passing along to
     *         a wrapped {@link Connection}.
     */
    public Connection wrapConnectionWithBufferWriter(final Connection connectionToWrap) {
        return new Connection() {
            final Connection wrappedConnection = connectionToWrap;

            @Override
            public void send(Event event) throws ConnectionException {
                // buffer before we attempt to send
                buffer.add(event);

                wrappedConnection.send(event);
            }

            @Override
            public void addEventSendFailureCallback(EventSendFailureCallback eventSendFailureCallback) {
                wrappedConnection.addEventSendFailureCallback(eventSendFailureCallback);
            }

            @Override
            public void close() throws IOException {
                wrappedConnection.close();
            }
        };
    }

    /**
     * Flusher is scheduled to periodically run by the BufferedConnection. It retrieves
     * an Iterator of Events from the underlying {@link Buffer} and attempts to send
     * one at time, discarding from the buffer on success.
     *
     * Upon the first failure, Flusher will return and wait to be run again in the future,
     * under the assumption that the network is now/still down.
     */
    private class Flusher implements Runnable {
        private long minAgeMillis;

        Flusher(long minAgeMillis) {
            this.minAgeMillis = minAgeMillis;
        }

        @Override
        public void run() {
            logger.trace("Running Flusher");

            RavenEnvironment.startManagingThread();
            try {
                Iterator<Event> events = buffer.getEvents();
                while (events.hasNext() && !closed) {
                    Event event = events.next();

                    long now = System.currentTimeMillis();
                    long eventTime = event.getTimestamp().getTime();
                    long age = now - eventTime;
                    if (age < minAgeMillis) {
                        logger.trace("Ignoring buffered event because it only " + age + "ms old.");
                        return;
                    }

                    try {
                        logger.trace("Flusher attempting to send Event: " + event.getId());
                        send(event);
                        logger.trace("Flusher successfully sent Event: " + event.getId());
                    } catch (Exception e) {
                        logger.debug("Flusher failed to send Event: " + event.getId(), e);

                        // Connectivity issues, give up until next Flusher run.
                        logger.trace("Flusher run exiting early.");
                        return;
                    }
                }
                logger.trace("Flusher run exiting, no more events to send.");
            } finally {
                RavenEnvironment.stopManagingThread();
            }
        }
    }

    private final class ShutDownHook extends Thread {

        /**
         * Whether or not this ShutDownHook instance will do anything when run.
         */
        private volatile boolean enabled = true;

        @Override
        public void run() {
            if (!enabled) {
                return;
            }

            RavenEnvironment.startManagingThread();
            try {
                // The current thread is managed by raven
                logger.info("Automatic shutdown of the buffered connection");
                BufferedConnection.this.close();
            } catch (Exception e) {
                logger.error("An exception occurred while closing the connection.", e);
            } finally {
                RavenEnvironment.stopManagingThread();
            }
        }
    }
}
