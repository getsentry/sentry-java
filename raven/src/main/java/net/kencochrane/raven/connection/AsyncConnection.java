package net.kencochrane.raven.connection;

import net.kencochrane.raven.Dsn;
import net.kencochrane.raven.event.Event;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous usage of a connection.
 * <p>
 * Instead of synchronously sending each event to a connection, use a ThreadPool to establish the connection
 * and submit the event.
 * </p>
 */
public class AsyncConnection implements Connection {
    /**
     * DSN option for the number of threads assigned for the connection.
     */
    public static final String MAX_THREADS_OPTION = "raven.async.threads";
    /**
     * DSN option for the priority of threads assigned for the connection.
     */
    public static final String PRIORITY_OPTION = "raven.async.priority";
    private static final Logger logger = Logger.getLogger(AsyncConnection.class.getCanonicalName());
    /**
     * Timeout of the {@link #executorService}.
     */
    private static final int TIMEOUT = 1000;
    /**
     * Number of threads dedicated to the connection usage by default (Number of processors available).
     */
    private static final int DEFAULT_MAX_THREADS = Runtime.getRuntime().availableProcessors();
    /**
     * Default threads priority.
     */
    private static final int DEFAULT_PRIORITY = Thread.MIN_PRIORITY;
    /**
     * Connection used to actually send the events.
     */
    private final Connection actualConnection;
    /**
     * Executor service in charge of running the connection in separate threads.
     */
    private final ExecutorService executorService;
    /**
     * Option to disable the propagation of the {@link #close()} operation to the actual connection.
     */
    private final boolean propagateClose;

    /**
     * Creates a connection which will rely on an executor to send events.
     * <p>
     * Will propagate the {@link #close()} operation and use {@link #DEFAULT_MAX_THREADS} and {@link #DEFAULT_PRIORITY}.
     * </p>
     *
     * @param actualConnection connection used to send the events.
     */
    public AsyncConnection(Connection actualConnection) {
        this(actualConnection, true, DEFAULT_MAX_THREADS, DEFAULT_PRIORITY);
    }

    /**
     * Creates a connection which will rely on an executor to send events.
     * <p>
     * Will propagate the {@link #close()} operation and
     * </p>
     *
     * @param actualConnection
     * @param dsn
     */
    public AsyncConnection(Connection actualConnection, Dsn dsn) {
        this(actualConnection, true, getMaxThreads(dsn), getPriority(dsn));
    }

    public AsyncConnection(Connection actualConnection, boolean propagateClose, int maxThreads, int priority) {
        this.actualConnection = actualConnection;
        this.propagateClose = propagateClose;
        executorService = Executors.newFixedThreadPool(maxThreads, new DaemonThreadFactory(priority));
        addShutdownHook();
    }

    private static int getMaxThreads(Dsn dsn) {
        if (dsn.getOptions().containsKey(MAX_THREADS_OPTION)) {
            return Integer.parseInt(dsn.getOptions().get(MAX_THREADS_OPTION));
        } else {
            return DEFAULT_MAX_THREADS;
        }
    }

    private static int getPriority(Dsn dsn) {
        if (dsn.getOptions().containsKey(PRIORITY_OPTION)) {
            return Integer.parseInt(dsn.getOptions().get(PRIORITY_OPTION));
        } else {
            return DEFAULT_PRIORITY;
        }
    }

    private void addShutdownHook() {
        // JUL loggers are shutdown by an other shutdown hook, it's possible that nothing will get actually logged.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    close();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "An exception occurred while closing the connection.", e);
                }
            }
        });
    }

    @Override
    public void send(Event event) {
        executorService.execute(new EventSubmitter(event));
    }

    /**
     * {@inheritDoc}.
     * <p>
     * Closing the {@link AsyncConnection} will attempt a graceful shutdown of the {@link #executorService} with a
     * timeout of {@link #TIMEOUT}, allowing the current events to be submitted while new events will be rejected.<br />
     * If the shutdown times out, the {@code executorService} will be forced to shutdown.
     * </p>
     */
    @Override
    public void close() throws IOException {
        logger.log(Level.INFO, "Gracefully shutdown sentry threads.");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS)) {
                logger.log(Level.WARNING, "Graceful shutdown took too much time, forcing the shutdown.");
                List<Runnable> tasks = executorService.shutdownNow();
                logger.log(Level.INFO, tasks.size() + " tasks failed to execute before the shutdown.");
            }
            logger.log(Level.SEVERE, "Shutdown interrupted.");
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Shutdown interrupted.");
        } finally {
            if (propagateClose)
                actualConnection.close();
        }
    }

    /**
     * Thread factory generating daemon threads with a custom priority.
     * <p>
     * Those (usually) low priority threads will allow to send event details to sentry concurrently without slowing
     * down the main application.
     * </p>
     */
    private static final class DaemonThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int priority;

        private DaemonThreadFactory(int priority) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" + POOL_NUMBER.getAndIncrement() + "-thread-";
            this.priority = priority;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (!t.isDaemon())
                t.setDaemon(true);
            if (t.getPriority() != priority)
                t.setPriority(priority);
            return t;
        }
    }

    /**
     * Simple runnable using the {@link #send(net.kencochrane.raven.event.Event)} method of the
     * {@link #actualConnection}.
     */
    private final class EventSubmitter implements Runnable {
        private final Event event;

        private EventSubmitter(Event event) {
            this.event = event;
        }

        @Override
        public void run() {
            try {
                actualConnection.send(event);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "An exception occurred while sending the event to Sentry.", e);
            }
        }
    }
}
