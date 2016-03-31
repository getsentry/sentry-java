package com.getsentry.raven.connection;

import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous usage of a connection.
 * <p>
 * Instead of synchronously sending each event to a connection, use a ThreadPool to establish the connection
 * and submit the event.
 */
public class AsyncConnection implements Connection {
    private static final Logger logger = LoggerFactory.getLogger(AsyncConnection.class);
    /**
     * Timeout of the {@link #executorService}.
     */
    private static final long SHUTDOWN_TIMEOUT = TimeUnit.SECONDS.toMillis(1);
    /**
     * Connection used to actually send the events.
     */
    private final Connection actualConnection;
    /**
     * Executor service in charge of running the connection in separate threads.
     */
    private final ExecutorService executorService;
    /**
     * Shutdown hook used to stop the async connection properly when the JVM quits.
     */
    private final ShutDownHook shutDownHook = new ShutDownHook();
    /**
     * Boolean that represents if graceful shutdown is enabled.
     */
    private boolean gracefulShutdown;
    /**
     * Boolean used to check whether the connection is still open or not.
     */
    private boolean closed;

    /**
     * Creates a connection which will rely on an executor to send events.
     * <p>
     * Will propagate the {@link #close()} operation.
     *
     * @param actualConnection connection used to send the events.
     * @param executorService  executorService used to process events, if null, the executorService will automatically
     *                         be set to {@code Executors.newSingleThreadExecutor()}
     * @param gracefulShutdown Indicates whether or not the shutdown operation should be managed by a ShutdownHook.
     */
    public AsyncConnection(Connection actualConnection, ExecutorService executorService, boolean gracefulShutdown) {
        this.actualConnection = actualConnection;
        if (executorService == null)
            this.executorService = Executors.newSingleThreadExecutor();
        else
            this.executorService = executorService;
        if (gracefulShutdown) {
            this.gracefulShutdown = gracefulShutdown;
            addShutdownHook();
        }
    }

    /**
     * Adds a hook to shutdown the {@link #executorService} gracefully when the JVM shuts down.
     */
    private void addShutdownHook() {
        // JUL loggers are shutdown by an other shutdown hook, it's possible that nothing will get actually logged.
        Runtime.getRuntime().addShutdownHook(shutDownHook);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The event will be added to a queue and will be handled by a separate {@code Thread} later on.
     */
    @Override
    public void send(Event event) {
        if (!closed)
            executorService.execute(new EventSubmitter(event));
    }

    /**
     * {@inheritDoc}.
     * <p>
     * Closing the {@link AsyncConnection} will attempt a graceful shutdown of the {@link #executorService} with a
     * timeout of {@link #SHUTDOWN_TIMEOUT}, allowing the current events to be submitted while new events will
     * be rejected.<br>
     * If the shutdown times out, the {@code executorService} will be forced to shutdown.
     */
    @Override
    public void close() throws IOException {
        if (gracefulShutdown)
            shutDownHook.enabled = false;
        doClose();
    }

    /**
     * Close the connection whether it's from the shutdown hook or not.
     *
     * @see #close()
     */
    private void doClose() throws IOException {
        logger.info("Gracefully shutdown sentry threads.");
        closed = true;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS)) {
                logger.warn("Graceful shutdown took too much time, forcing the shutdown.");
                List<Runnable> tasks = executorService.shutdownNow();
                logger.info("{} tasks failed to execute before the shutdown.", tasks.size());
            }
            logger.info("Shutdown finished.");
        } catch (InterruptedException e) {
            logger.error("Graceful shutdown interrupted, forcing the shutdown.");
            List<Runnable> tasks = executorService.shutdownNow();
            logger.info("{} tasks failed to execute before the shutdown.", tasks.size());
        } finally {
            actualConnection.close();
        }
    }

    /**
     * Simple runnable using the {@link #send(com.getsentry.raven.event.Event)} method of the
     * {@link #actualConnection}.
     */
    private final class EventSubmitter implements Runnable {
        private final Event event;

        private EventSubmitter(Event event) {
            this.event = event;
        }

        @Override
        public void run() {
            RavenEnvironment.startManagingThread();
            try {
                // The current thread is managed by raven
                actualConnection.send(event);
            } catch (Exception e) {
                logger.error("An exception occurred while sending the event to Sentry.", e);
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
                logger.info("Automatic shutdown of the async connection");
                AsyncConnection.this.doClose();
            } catch (Exception e) {
                logger.error("An exception occurred while closing the connection.", e);
            } finally {
                RavenEnvironment.stopManagingThread();
            }
        }
    }
}
