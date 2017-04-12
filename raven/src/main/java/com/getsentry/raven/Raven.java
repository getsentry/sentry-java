package com.getsentry.raven;

import com.getsentry.raven.connection.Connection;
import com.getsentry.raven.connection.LockedDownException;
import com.getsentry.raven.context.Context;
import com.getsentry.raven.context.ContextManager;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.helper.EventBuilderHelper;
import com.getsentry.raven.event.interfaces.ExceptionInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raven is a client for Sentry allowing to send an {@link Event} that will be processed and sent to a Sentry server.
 * <p>
 * It is recommended to create an instance of Raven through
 * {@link RavenFactory#createRavenInstance(com.getsentry.raven.dsn.Dsn)}, this will use the best factory available to
 * create a sensible instance of Raven.
 */
public class Raven {
    private static final Logger logger = LoggerFactory.getLogger(Raven.class);
    // CHECKSTYLE.OFF: ConstantName
    private static final Logger lockdownLogger = LoggerFactory.getLogger(Raven.class.getName() + ".lockdown");
    // CHECKSTYLE.ON: ConstantName
    /**
     * The most recently constructed Raven instance, used by static helper methods like {@link Raven#capture(Event)}.
     */
    private static volatile Raven stored = null;
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
     * such as {@link com.getsentry.raven.event.Breadcrumb}s.
     */
    private final ContextManager contextManager;
    /**
     * Constructs a Raven instance using the provided connection.
     *
     * Note that the most recently constructed instance is stored statically so it can be used with
     * the static helper methods.
     *
     * @param connection Underlying {@link Connection} instance to use for sending events
     * @param contextManager {@link ContextManager} instance to use for storing contextual data
     */
    public Raven(Connection connection, ContextManager contextManager) {
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
     * Closes the connection for the Raven instance.
     */
    public void closeConnection() {
        try {
            connection.close();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't close the Raven connection", e);
        }
    }

    public Context getContext() {
        return contextManager.getContext();
    }

    @Override
    public String toString() {
        return "Raven{"
                + "name=" + RavenEnvironment.getRavenName()
                + ", connection=" + connection
                + ", contextManager=" + contextManager
                + '}';
    }

    // --------------------------------------------------------
    // Static helper methods follow
    // --------------------------------------------------------

    /**
     * Returns the last statically stored Raven instance or null if one has
     * never been stored.
     *
     * @return statically stored {@link Raven} instance
     */
    public static Raven getStoredInstance() {
        return stored;
    }

    private static void verifyStoredInstance() {
        if (stored == null) {
            throw new NullPointerException("No stored Raven instance is available to use."
                + " You must construct a Raven instance before using the static Raven methods.");
        }
    }

    /**
     * Send an Event using the statically stored Raven instance.
     *
     * @param event Event to send to the Sentry server
     */
    public static void capture(Event event) {
        verifyStoredInstance();
        getStoredInstance().sendEvent(event);
    }

    /**
     * Sends an exception (or throwable) to the Sentry server using the statically stored Raven instance.
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
     * Sends a message to the Sentry server using the statically stored Raven instance.
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
     * Builds and sends an {@link Event} to the Sentry server using the statically stored Raven instance.
     *
     * @param eventBuilder {@link EventBuilder} to send to Sentry.
     */
    public static void capture(EventBuilder eventBuilder) {
        verifyStoredInstance();
        getStoredInstance().sendEvent(eventBuilder);
    }

    /**
     * Record a {@link Breadcrumb}.
     *
     * @param breadcrumb Breadcrumb to record
     */
    public static void record(Breadcrumb breadcrumb) {
        getStoredInstance().getContext().recordBreadcrumb(breadcrumb);
    }

    /**
     * Clear the statically stored Raven instance.
     */
    public static void clearStoredRaven() {
        if (stored != null) {
            stored.closeConnection();
        }
        stored = null;
    }

}
