package net.kencochrane.raven.connection;

import net.kencochrane.raven.event.LoggedEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
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
    private static final Logger logger = Logger.getLogger(AsyncConnection.class.getCanonicalName());
    /**
     * Connection used to actually send the events.
     */
    private final Connection actualConnection;
    private final ExecutorService executorService =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new DaemonThreadFactory());

    /**
     * Create a connection which will rely on an executor to send events.
     *
     * @param actualConnection connection used to send the events.
     */
    public AsyncConnection(Connection actualConnection) {
        this.actualConnection = actualConnection;
    }

    @Override
    public void send(LoggedEvent event) {
        // TODO: Consider adding an option to wait when it's full?
        executorService.execute(new LoggedEventSubmitter(event));
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        private DaemonThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" + poolNumber.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (!t.isDaemon())
                t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    /**
     * Simple runnable using the {@link #send(net.kencochrane.raven.event.LoggedEvent)} method of the
     * {@link #actualConnection}.
     */
    private final class LoggedEventSubmitter implements Runnable {
        private final LoggedEvent event;

        private LoggedEventSubmitter(LoggedEvent event) {
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
