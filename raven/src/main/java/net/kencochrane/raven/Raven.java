package net.kencochrane.raven;

import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.helper.EventBuilderHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Raven is a client for Sentry allowing to send an {@link Event} that will be processed and sent to a Sentry server.
 * <p>
 * It is recommended to create an instance of Raven through {@link RavenFactory#createRavenInstance(Dsn)}, this
 * will use the best factory available to create a sensible instance of Raven.
 * </p>
 */
public class Raven {
    /**
     * Indicates whether the current thread has been spawned within raven or not.
     */
    public static final ThreadLocal<Boolean> RAVEN_THREAD = new ThreadLocal<Boolean>(){
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };
    /**
     * Version of this client, the major version is the current supported Sentry protocol, the minor version changes
     * for each release of this project.
     */
    public static final String NAME = "Raven-Java/4.0";
    private static final Logger logger = LoggerFactory.getLogger(Raven.class);
    private final Set<EventBuilderHelper> builderHelpers = new HashSet<EventBuilderHelper>();
    private Connection connection;

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
            logger.error("An exception occurred while sending the event to Sentry.", e);
        }
    }

    /**
     * Removes a builder helper.
     *
     * @param builderHelper builder helper to remove.
     */
    public void removeBuilderHelper(EventBuilderHelper builderHelper) {
        logger.info("Removes '" + builderHelper + "' to the list of builder helpers.");
        builderHelpers.remove(builderHelper);
    }

    /**
     * Adds a builder helper.
     *
     * @param builderHelper builder helper to add.
     */
    public void addBuilderHelper(EventBuilderHelper builderHelper) {
        logger.info("Adding '" + builderHelper + "' to the list of builder helpers.");
        builderHelpers.add(builderHelper);
    }

    public Set<EventBuilderHelper> getBuilderHelpers() {
        return Collections.unmodifiableSet(builderHelpers);
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }
}
