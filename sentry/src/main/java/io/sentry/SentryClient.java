package io.sentry;

import io.sentry.connection.Connection;
import io.sentry.connection.EventSendCallback;
import io.sentry.connection.LockedDownException;
import io.sentry.connection.TooManyRequestsException;
import io.sentry.context.Context;
import io.sentry.context.ContextManager;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.helper.EventBuilderHelper;
import io.sentry.event.helper.ShouldSendEventCallback;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sentry client, for sending {@link Event}s to a Sentry server.
 * <p>
 * It is recommended that you create an instance of Sentry through
 * {@link SentryClientFactory#createSentryClient(io.sentry.dsn.Dsn)}, which will use the best factory available.
 */
public class SentryClient {
    private static final Logger logger = LoggerFactory.getLogger(SentryClient.class);
    // CHECKSTYLE.OFF: ConstantName
    private static final Logger lockdownLogger = LoggerFactory.getLogger(SentryClient.class.getName() + ".lockdown");
    // CHECKSTYLE.ON: ConstantName
    /**
     * Identifies the version of the application.
     * <p>
     * Might be null in which case the release information will not be sent with the event.
     */
    protected String release;
    /**
     * Identifies the distribution of the application.
     * <p>
     * Might be null in which case the release distribution will not be sent with the event.
     */
    protected String dist;
    /**
     * Identifies the environment the application is running in.
     * <p>
     * Might be null in which case the environment information will not be sent with the event.
     */
    protected String environment;
    /**
     * Server name to be sent to sentry.
     * <p>
     * Might be null in which case the hostname is found via a reverse DNS lookup.
     */
    protected String serverName;
    /**
     * Additional tags to be sent to sentry.
     * <p>
     * Might be empty in which case no tags are added to the events.
     */
    protected Map<String, String> tags = new HashMap<>();
    /**
     * Tags to extract from the MDC system and set on {@link io.sentry.event.Event}s, where applicable.
     */
    protected Set<String> mdcTags = new HashSet<>();
    /**
     * Extra data to be sent to sentry.
     * <p>
     * Might be empty in which case no extra data is added to the events.
     */
    protected Map<String, Object> extra = new HashMap<>();
    /**
     * Set of callbacks that are checked before each {@link Event} is sent to Sentry.
     */
    private final Set<ShouldSendEventCallback> shouldSendEventCallbacks = new HashSet<>();
    /**
     * The underlying {@link Connection} to use for sending events to Sentry.
     */
    private final Connection connection;
    /**
     * List of {@link EventBuilderHelper}s.
     */
    private final List<EventBuilderHelper> builderHelpers = new CopyOnWriteArrayList<>();
    /**
     * The {@link ContextManager} to use for locating and storing data that is context specific,
     * such as {@link io.sentry.event.Breadcrumb}s.
     */
    private final ContextManager contextManager;
    /**
     * Reference to the {@link SentryUncaughtExceptionHandler} if one was enabled, or null.
     */
    private SentryUncaughtExceptionHandler uncaughtExceptionHandler;

    /**
     * Constructs a {@link SentryClient} instance using the provided connection.
     *
     * Note that the most recently constructed instance is stored statically so it can be used with
     * the static helper methods.
     *
     * @param connection Underlying {@link Connection} instance to use for sending events
     * @param contextManager {@link ContextManager} instance to use for storing contextual data
     */
    public SentryClient(Connection connection, ContextManager contextManager) {
        this.connection = connection;
        this.contextManager = contextManager;
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
        for (ShouldSendEventCallback shouldSendEventCallback : shouldSendEventCallbacks) {
            if (!shouldSendEventCallback.shouldSend(event)) {
                logger.trace("Not sending Event because of ShouldSendEventCallback: {}", shouldSendEventCallback);
                return;
            }
        }

        try {
            connection.send(event);
        } catch (LockedDownException | TooManyRequestsException e) {
            logger.debug("Dropping an Event due to lockdown: " + event);
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
        if (!Util.isNullOrEmpty(release)) {
            eventBuilder.withRelease(release.trim());
            if (!Util.isNullOrEmpty(dist)) {
                eventBuilder.withDist(dist.trim());
            }
        }

        if (!Util.isNullOrEmpty(environment)) {
            eventBuilder.withEnvironment(environment.trim());
        }

        if (!Util.isNullOrEmpty(serverName)) {
            eventBuilder.withServerName(serverName.trim());
        }

        for (Map.Entry<String, String> tagEntry : tags.entrySet()) {
            eventBuilder.withTag(tagEntry.getKey(), tagEntry.getValue());
        }

        for (Map.Entry<String, Object> extraEntry : extra.entrySet()) {
            eventBuilder.withExtra(extraEntry.getKey(), extraEntry.getValue());
        }

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
        sendEvent(eventBuilder);
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
        sendEvent(eventBuilder);
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

    public List<EventBuilderHelper> getBuilderHelpers() {
        return Collections.unmodifiableList(builderHelpers);
    }

    /**
     * Closes the connection for the {@link SentryClient} instance.
     */
    public void closeConnection() {
        if (uncaughtExceptionHandler != null) {
            uncaughtExceptionHandler.disable();
        }

        try {
            connection.close();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't close the Sentry connection", e);
        }
    }

    /**
     * Clears the {@link ContextManager} data.
     */
    public void clearContext() {
        contextManager.clear();
    }

    public Context getContext() {
        return contextManager.getContext();
    }

    public String getRelease() {
        return release;
    }

    public String getDist() {
        return dist;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getServerName() {
        return serverName;
    }

    public Map<String, String> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    public Set<String> getMdcTags() {
        return Collections.unmodifiableSet(mdcTags);
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setRelease(String release) {
        this.release = release;
    }

    public void setDist(String dist) {
        this.dist = dist;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * Add a tag that will be sent with all future {@link Event}s.
     *
     * @param name Tag name
     * @param value Tag value
     */
    public void addTag(String name, String value) {
        this.tags.put(name, value);
    }

    /**
     * Set the tags that will be sent with all future {@link Event}s.
     *
     * @param tags Map of tags
     */
    public void setTags(Map<String, String> tags) {
        if (tags == null) {
            this.tags = new HashMap<>();
        } else {
            this.tags = tags;
        }
    }

    /**
     * Set the tags to extract from the MDC system and set on {@link io.sentry.event.Event}s, where applicable.
     *
     * @param extraTags Set of tags to extract from the MDC system
     * @deprecated prefer {@link SentryClient#setMdcTags(Set)}
     */
    @Deprecated
    public void setExtraTags(Set<String> extraTags) {
        setMdcTags(extraTags);
    }

    /**
     * Set the tags to extract from the MDC system and set on {@link io.sentry.event.Event}s, where applicable.
     *
     * @param mdcTags Set of tags to extract from the MDC system
     */
    public void setMdcTags(Set<String> mdcTags) {
        if (mdcTags == null) {
            this.mdcTags = new HashSet<>();
        } else {
            this.mdcTags = mdcTags;
        }
    }

    /**
     * Add a tag to extract from the MDC system and set on {@link io.sentry.event.Event}s, where applicable.
     *
     * @param extraName Tag name to extract from the MDC system
     * @deprecated prefer {@link SentryClient#addMdcTag(String)}
     */
    @Deprecated
    public void addExtraTag(String extraName) {
        addMdcTag(extraName);
    }

    /**
     * Add a tag to extract from the MDC system and set on {@link io.sentry.event.Event}s, where applicable.
     *
     * @param tagName Tag name to extract from the MDC system
     */
    public void addMdcTag(String tagName) {
        this.mdcTags.add(tagName);
    }

    /**
     * Add to the extra data that will be sent with all future {@link Event}s.
     *
     * @param name Data name
     * @param value Data value
     */
    public void addExtra(String name, Object value) {
        this.extra.put(name, value);
    }

    /**
     * Set the extra data that will be sent with all future {@link Event}s.
     *
     * @param extra Map of extra data
     */
    public void setExtra(Map<String, Object> extra) {
        if (extra == null) {
            this.extra = new HashMap<>();
        } else {
            this.extra = extra;
        }
    }

    /**
     * Add a callback that is called after an {@link Event} is sent to Sentry.
     *
     * @param eventSendCallback callback instance
     */
    public void addEventSendCallback(EventSendCallback eventSendCallback) {
        connection.addEventSendCallback(eventSendCallback);
    }

    /**
     * Add a callback that is called before an {@link Event} is sent to Sentry.
     *
     * @param shouldSendEventCallback callback instance
     */
    public void addShouldSendEventCallback(ShouldSendEventCallback shouldSendEventCallback) {
        shouldSendEventCallbacks.add(shouldSendEventCallback);
    }

    /**
     * Setup and store the {@link SentryUncaughtExceptionHandler} so that it can be
     * disabled on {@link SentryClient#closeConnection()}.
     */
    protected void setupUncaughtExceptionHandler() {
        uncaughtExceptionHandler = SentryUncaughtExceptionHandler.setup();
    }

    @Override
    public String toString() {
        return "SentryClient{"
            + "release='" + release + '\''
            + ", dist='" + dist + '\''
            + ", environment='" + environment + '\''
            + ", serverName='" + serverName + '\''
            + ", tags=" + tags
            + ", mdcTags=" + mdcTags
            + ", extra=" + extra
            + ", connection=" + connection
            + ", builderHelpers=" + builderHelpers
            + ", contextManager=" + contextManager
            + ", uncaughtExceptionHandler=" + uncaughtExceptionHandler
            + '}';
    }
}
