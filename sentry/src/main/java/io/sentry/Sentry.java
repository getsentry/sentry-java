package io.sentry;

import io.sentry.connection.Connection;
import io.sentry.connection.LockedDownException;
import io.sentry.context.Context;
import io.sentry.context.ContextManager;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.helper.EventBuilderHelper;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.SentryException;
import io.sentry.event.interfaces.SentryStackTraceElement;
import io.sentry.event.interfaces.StackTraceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sentry is a client for Sentry allowing to send an {@link Event} that will be processed and sent to a Sentry server.
 * <p>
 * It is recommended to create an instance of Sentry through
 * {@link SentryFactory#createSentryInstance(io.sentry.dsn.Dsn)}, this will use the best factory available to
 * create a sensible instance of Sentry.
 */
public class Sentry {
    private static final Logger logger = LoggerFactory.getLogger(Sentry.class);
    // CHECKSTYLE.OFF: ConstantName
    private static final Logger lockdownLogger = LoggerFactory.getLogger(Sentry.class.getName() + ".lockdown");
    // CHECKSTYLE.ON: ConstantName
    /**
     * The most recently constructed Sentry instance, used by static helper methods like {@link Sentry#capture(Event)}.
     */
    private static volatile Sentry stored = null;
    /**
     * The underlying {@link Connection} to use for sending events to Sentry.
     */
    private volatile Connection connection;
    /**
     * Set of {@link EventBuilderHelper}s. Note that we wrap a {@link ConcurrentHashMap} because there
     * isn't a concurrent set in the standard library.
     */
    private final Set<EventBuilderHelper> builderHelpers =
        Collections.newSetFromMap(new ConcurrentHashMap<EventBuilderHelper, Boolean>());
    /**
     * The {@link ContextManager} to use for locating and storing data that is context specific,
     * such as {@link io.sentry.event.Breadcrumb}s.
     */
    private final ContextManager contextManager;
    /**
     * Constructs a Sentry instance using the provided connection.
     *
     * Note that the most recently constructed instance is stored statically so it can be used with
     * the static helper methods.
     *
     * @param connection Underlying {@link Connection} instance to use for sending events
     * @param contextManager {@link ContextManager} instance to use for storing contextual data
     */
    public Sentry(Connection connection, ContextManager contextManager) {
        this.connection = connection;
        this.contextManager = contextManager;
        stored = this;
    }

    /**
     * Runs the {@link EventBuilderHelper} against the {@link EventBuilder} to obtain additional information with a
     * MDC-like system.
     *
     * @param eventBuilder event builder containing a not yet finished event.
     */
    public void runBuilderHelpers(EventBuilder eventBuilder) {
        for (EventBuilderHelper builderHelper : builderHelpers) {
            builderHelper.helpBuildingEvent(eventBuilder);
        }
    }

    /**
     * Sends a built {@link Event} to the Sentry server.
     *
     * @param event event to send to Sentry.
     */
    public void sendEvent(Event event) {
        try {
            connection.send(event);
        } catch (LockedDownException e) {
            lockdownLogger.warn("The connection to Sentry is currently locked down.", e);
        } catch (Exception e) {
            logger.error("An exception occurred while sending the event to Sentry.", e);
        } finally {
            getContext().setLastEventId(event.getId());
        }
    }

    /**
     * Builds and sends an {@link Event} to the Sentry server.
     *
     * @param eventBuilder {@link EventBuilder} to send to Sentry.
     */
    public void sendEvent(EventBuilder eventBuilder) {
        runBuilderHelpers(eventBuilder);
        Event event = eventBuilder.build();
        sendEvent(event);
    }

    /**
     * Sends a message to the Sentry server.
     * <p>
     * The message will be logged at the {@link Event.Level#INFO} level.
     *
     * @param message message to send to Sentry.
     */
    public void sendMessage(String message) {
        EventBuilder eventBuilder = new EventBuilder().withMessage(message)
            .withLevel(Event.Level.INFO);
        runBuilderHelpers(eventBuilder);
        Event event = eventBuilder.build();
        sendEvent(event);
    }

    /**
     * Sends an exception (or throwable) to the Sentry server.
     * <p>
     * The exception will be logged at the {@link Event.Level#ERROR} level.
     *
     * @param throwable exception to send to Sentry.
     */
    public void sendException(Throwable throwable) {
        EventBuilder eventBuilder = new EventBuilder().withMessage(throwable.getMessage())
            .withLevel(Event.Level.ERROR)
            .withSentryInterface(new ExceptionInterface(throwable));
        runBuilderHelpers(eventBuilder);
        Event event = eventBuilder.build();
        sendEvent(event);
    }

    /**
     * Creates an exception, builds an event and sends it to the Sentry server.
     *
     * @param message String
     * @param level {@link Event.Level}
     * @param frames {@link SentryStackTraceElement}
     */
    public void sendException(String message, Event.Level level, SentryStackTraceElement[] frames) {
        StackTraceInterface stackTraceInterface = new StackTraceInterface(frames);
        Deque<SentryException> exceptions = new ArrayDeque<>();
        exceptions.push(new SentryException(message, "", "", stackTraceInterface));
        EventBuilder eventBuilder = new EventBuilder().withMessage(message)
                .withLevel(level)
                .withSentryInterface(new ExceptionInterface(exceptions));
        runBuilderHelpers(eventBuilder);
        Event event = eventBuilder.build();
        sendEvent(event);
    }

    /**
     * Removes a builder helper.
     *
     * @param builderHelper builder helper to remove.
     */
    public void removeBuilderHelper(EventBuilderHelper builderHelper) {
        logger.debug("Removing '{}' from the list of builder helpers.", builderHelper);
        builderHelpers.remove(builderHelper);
    }

    /**
     * Adds a builder helper.
     *
     * @param builderHelper builder helper to add.
     */
    public void addBuilderHelper(EventBuilderHelper builderHelper) {
        logger.debug("Adding '{}' to the list of builder helpers.", builderHelper);
        builderHelpers.add(builderHelper);
    }

    public Set<EventBuilderHelper> getBuilderHelpers() {
        return Collections.unmodifiableSet(builderHelpers);
    }

    /**
     * Closes the connection for the Sentry instance.
     */
    public void closeConnection() {
        try {
            connection.close();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't close the Sentry connection", e);
        }
    }

    public Context getContext() {
        return contextManager.getContext();
    }

    @Override
    public String toString() {
        return "Sentry{"
                + "name=" + SentryEnvironment.getSentryName()
                + ", connection=" + connection
                + ", contextManager=" + contextManager
                + '}';
    }

    // --------------------------------------------------------
    // Static helper methods follow
    // --------------------------------------------------------

    /**
     * Returns the last statically stored Sentry instance or null if one has
     * never been stored.
     *
     * @return statically stored {@link Sentry} instance
     */
    public static Sentry getStoredInstance() {
        return stored;
    }

    private static void verifyStoredInstance() {
        if (stored == null) {
            throw new NullPointerException("No stored Sentry instance is available to use."
                + " You must construct a Sentry instance before using the static Sentry methods.");
        }
    }

    /**
     * Send an Event using the statically stored Sentry instance.
     *
     * @param event Event to send to the Sentry server
     */
    public static void capture(Event event) {
        verifyStoredInstance();
        getStoredInstance().sendEvent(event);
    }

    /**
     * Sends an exception (or throwable) to the Sentry server using the statically stored Sentry instance.
     * <p>
     * The exception will be logged at the {@link Event.Level#ERROR} level.
     *
     * @param throwable exception to send to Sentry.
     */
    public static void capture(Throwable throwable) {
        verifyStoredInstance();
        getStoredInstance().sendException(throwable);
    }

    /**
     * Sends a message to the Sentry server using the statically stored Sentry instance.
     * <p>
     * The message will be logged at the {@link Event.Level#INFO} level.
     *
     * @param message message to send to Sentry.
     */
    public static void capture(String message) {
        verifyStoredInstance();
        getStoredInstance().sendMessage(message);
    }

    /**
     * Builds and sends an {@link Event} to the Sentry server using the statically stored Sentry instance.
     *
     * @param eventBuilder {@link EventBuilder} to send to Sentry.
     */
    public static void capture(EventBuilder eventBuilder) {
        verifyStoredInstance();
        getStoredInstance().sendEvent(eventBuilder);
    }

}
