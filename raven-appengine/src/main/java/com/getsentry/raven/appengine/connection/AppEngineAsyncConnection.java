package com.getsentry.raven.appengine.connection;

import com.getsentry.raven.connection.EventSendFailureCallback;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.getsentry.raven.connection.Connection;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.google.appengine.api.taskqueue.DeferredTaskContext.setDoNotRetry;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withPayload;

/**
 * Asynchronous usage of a connection withing Google App Engine.
 * <p>
 * Instead of synchronously sending each event to a connection, use a the task queue system to establish the connection
 * and submit the event.
 * <p>
 * Google App Engine serialises the tasks before queuing them, to keep a link between the task and the
 * {@link AppEngineAsyncConnection} associated, a register of the instances of {@code AppEngineAsyncConnection} is
 * kept in {@link #APP_ENGINE_ASYNC_CONNECTIONS}.<br>
 * This register is populated when a new instance of {@code AppEngineAsyncConnection} is created and the connection
 * is removed from the register when it has been closed with {@link #close()}.<br>
 * The register works based on identifier defined by the user. There is no ID conflict handling, the user is expected
 * to manage the uniqueness of those ID.
 */
public class AppEngineAsyncConnection implements Connection {
    private static final Logger logger = LoggerFactory.getLogger(AppEngineAsyncConnection.class);
    private static final Map<String, AppEngineAsyncConnection> APP_ENGINE_ASYNC_CONNECTIONS = new HashMap<>();
    /**
     * Identifier of the async connection.
     */
    private final String id;
    /**
     * Connection used to actually send the events.
     */
    private final Connection actualConnection;
    /**
     * Queue used to send deferred tasks.
     */
    private Queue queue = QueueFactory.getDefaultQueue();
    /**
     * Boolean used to check whether the connection is still open or not.
     */
    private boolean closed;

    /**
     * Creates a connection which will rely on an executor to send events.
     * <p>
     * Will propagate the {@link #close()} operation.
     *
     * @param id               Identifier of the connection shared across all the instances of the application.
     * @param actualConnection Connection used to send the events.
     */
    public AppEngineAsyncConnection(String id, Connection actualConnection) {
        this.actualConnection = actualConnection;
        this.id = id;
        APP_ENGINE_ASYNC_CONNECTIONS.put(id, this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The event will be added to a queue and will be handled by a separate {@code Thread} later on.
     */
    @Override
    public void send(Event event) {
        if (!closed) {
            queue.add(withPayload(new EventSubmitter(id, event)));
        }
    }

    @Override
    public void addEventSendFailureCallback(EventSendFailureCallback eventSendFailureCallback) {
        actualConnection.addEventSendFailureCallback(eventSendFailureCallback);
    }

    /**
     * {@inheritDoc}.
     * <p>
     * Closing the {@link AppEngineAsyncConnection} will gracefully remove every task created earlier from the queue.
     */
    @Override
    public void close() throws IOException {
        logger.info("Gracefully stopping sentry tasks.");
        closed = true;
        actualConnection.close();
        APP_ENGINE_ASYNC_CONNECTIONS.remove(id);
    }

    /**
     * Set the queue used to send EventSubmitter tasks.
     *
     * @param queueName name of the queue to use.
     */
    public void setQueue(String queueName) {
        this.queue = QueueFactory.getQueue(queueName);
    }

    /**
     * Simple DeferredTask using the {@link #send(Event)} method of the {@link #actualConnection}.
     */
    private static final class EventSubmitter implements DeferredTask {
        private final String connectionId;
        private final Event event;

        private EventSubmitter(String connectionId, Event event) {
            this.connectionId = connectionId;
            this.event = event;
        }

        @Override
        public void run() {
            setDoNotRetry(true);
            RavenEnvironment.startManagingThread();
            try {
                // The current thread is managed by raven
                AppEngineAsyncConnection connection = APP_ENGINE_ASYNC_CONNECTIONS.get(connectionId);
                if (connection == null) {
                    logger.warn("Couldn't find the AppEngineAsyncConnection identified by '{}'. "
                            + "The connection has probably been closed.", connectionId);
                    return;
                }
                connection.actualConnection.send(event);
            } catch (Exception e) {
                logger.error("An exception occurred while sending the event to Sentry.", e);
            } finally {
                RavenEnvironment.stopManagingThread();
            }
        }
    }
}
