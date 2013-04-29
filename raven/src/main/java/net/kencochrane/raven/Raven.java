package net.kencochrane.raven;

import net.kencochrane.raven.connection.AsyncConnection;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.connection.HttpConnection;
import net.kencochrane.raven.connection.UdpConnection;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.helper.EventBuilderHelper;
import net.kencochrane.raven.marshaller.json.JsonMarshaller;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Raven is a client for Sentry allowing to send an {@link Event} that will be processed and sent to a Sentry server.
 * <p>
 * A default client will use the protocol defined in the DSN and will send the content in the JSON format
 * (optionally compressed and encoded in base64).
 * </p>
 */
public class Raven {
    /**
     * Version of this client, the major version is the current supported Sentry protocol, the minor version changes
     * for each release of this project.
     */
    public static final String NAME = "Raven-Java/4.0";
    private static final Logger logger = Logger.getLogger(Raven.class.getCanonicalName());
    private final Set<EventBuilderHelper> builderHelpers = new HashSet<EventBuilderHelper>();
    private Connection connection;

    /**
     * Builds a default Raven client, trying to figure out which {@link Dsn} can be used.
     */
    public Raven() {
    }

    /**
     * Builds a Raven client using the given connection.
     *
     * @param connection connection to sentry.
     */
    public Raven(Connection connection) {
        this.connection = connection;
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
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An exception occurred while sending the event to Sentry.", e);
        }
    }

    /**
     * Removes a builder helper.
     *
     * @param builderHelper builder helper to remove.
     */
    public void removeBuilderHelper(EventBuilderHelper builderHelper) {
        logger.log(Level.INFO, "Removes '" + builderHelper + "' to the list of builder helpers.");
        builderHelpers.remove(builderHelper);
    }

    /**
     * Adds a builder helper.
     *
     * @param builderHelper builder helper to add.
     */
    public void addBuilderHelper(EventBuilderHelper builderHelper) {
        logger.log(Level.INFO, "Adding '" + builderHelper + "' to the list of builder helpers.");
        builderHelpers.add(builderHelper);
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Set<EventBuilderHelper> getBuilderHelpers() {
        return Collections.unmodifiableSet(builderHelpers);
    }
}
