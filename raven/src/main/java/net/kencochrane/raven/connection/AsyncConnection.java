package net.kencochrane.raven.connection;

import net.kencochrane.raven.event.LoggedEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous usage of a connection.
 * <p>
 * Instead of synchronously sending each event to a connection, use a ThreadPool to establish the connection
 * and submit the event.
 * </p>
 */
public class AsyncConnection implements Connection {
    /**
     * Connection used to actually send the events.
     */
    private final Connection actualConnection;
    // TODO: Allow the user to change to a custom executor?
    private final ExecutorService executorService = Executors.newCachedThreadPool();

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
            actualConnection.send(event);
        }
    }
}
