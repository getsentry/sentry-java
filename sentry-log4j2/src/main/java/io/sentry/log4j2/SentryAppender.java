package io.sentry.log4j2;

import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import io.sentry.config.Lookup;
import io.sentry.dsn.Dsn;
import io.sentry.dsn.InvalidDsnException;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.MessageInterface;
import io.sentry.event.interfaces.StackTraceInterface;
import io.sentry.util.Util;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Appender for log4j2 in charge of sending the logged events to a Sentry server.
 */
@Plugin(name = "Sentry", category = "Core", elementType = "appender", printObject = true)
public class SentryAppender extends AbstractAppender {
    /**
     * Default name for the appender.
     */
    public static final String APPENDER_NAME = "sentry";
    /**
     * Name of the {@link Event#extra} property containing NDC details.
     */
    public static final String LOG4J_NDC = "log4j2-NDC";
    /**
     * Name of the {@link Event#extra} property containing Marker details.
     */
    public static final String LOG4J_MARKER = "log4j2-Marker";
    /**
     * Name of the {@link Event#extra} property containing the Thread name.
     */
    public static final String THREAD_NAME = "Sentry-Threadname";
    /**
     * Current instance of {@link SentryClient}.
     *
     * @see #initSentry()
     */
    protected volatile SentryClient sentryClient;
    /**
     * DSN property of the appender.
     * <p>
     * Might be null in which case the DSN should be detected automatically.
     */
    protected String dsn;
    /**
     * Name of the {@link SentryClientFactory} being used.
     * <p>
     * Might be null in which case the factory should be defined automatically.
     */
    protected String sentryClientFactory;
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
     * Might be empty in which case no tags are sent.
     */
    protected Map<String, String> tags = Collections.emptyMap();
    /**
     * Set of tags to look for in the Thread Context Map. These will be added as tags to be sent to Sentry.
     * <p>
     * Might be empty in which case no mapped tags are set.
     * </p>
     */
    protected Set<String> extraTags = Collections.emptySet();
    /**
     * Used for lazy initialization of appender state, see {@link #lazyInit()}.
     */
    private volatile boolean initialized = false;

    /**
     * Creates an instance of SentryAppender.
     */
    public SentryAppender() {
        this(APPENDER_NAME, null);
    }

    /**
     * Creates an instance of SentryAppender.
     *
     * @param sentryClient instance of Sentry to use with this appender.
     */
    public SentryAppender(SentryClient sentryClient) {
        this();
        this.sentryClient = sentryClient;
    }

    /**
     * Creates an instance of SentryAppender.
     *
     * @param name The Appender name.
     * @param filter The Filter to associate with the Appender.
     */
    protected SentryAppender(String name, Filter filter) {
        super(name, filter, null, true);
        this.addFilter(new DropSentryFilter());
    }

    /**
     * Create a Sentry Appender.
     *
     * @param name                The name of the Appender.
     * @param dsn                 Data Source Name to access the Sentry server.
     * @param sentryClientFactory Name of the factory to use to build the {@link SentryClient} instance.
     * @param release             Release to be sent to Sentry.
     * @param dist                Dist to be sent to Sentry.
     * @param environment         Environment to be sent to Sentry.
     * @param serverName          serverName to be sent to Sentry.
     * @param tags                Tags to add to each event.
     * @param extraTags           Tags to search through the Thread Context Map.
     * @param filter              The filter, if any, to use.
     * @return The SentryAppender.
     */
    @PluginFactory
    @SuppressWarnings("checkstyle:parameternumber")
    public static SentryAppender createAppender(@PluginAttribute("name") final String name,
                                                @PluginAttribute("dsn") final String dsn,
                                                @PluginAttribute("factory") final String sentryClientFactory,
                                                @PluginAttribute("release") final String release,
                                                @PluginAttribute("dist") final String dist,
                                                @PluginAttribute("environment") final String environment,
                                                @PluginAttribute("serverName") final String serverName,
                                                @PluginAttribute("tags") final String tags,
                                                @PluginAttribute("extraTags") final String extraTags,
                                                @PluginElement("filters") final Filter filter) {

        if (name == null) {
            LOGGER.error("No name provided for SentryAppender");
            return null;
        }
        SentryAppender sentryAppender = new SentryAppender(name, filter);
        sentryAppender.setDsn(dsn);

        if (release != null) {
            sentryAppender.setRelease(release);
        }
        if (dist != null) {
            sentryAppender.setDist(dist);
        }
        if (environment != null) {
            sentryAppender.setEnvironment(environment);
        }
        if (serverName != null) {
            sentryAppender.setServerName(serverName);
        }
        if (tags != null) {
            sentryAppender.setTags(tags);
        }
        if (extraTags != null) {
            sentryAppender.setExtraTags(extraTags);
        }
        sentryAppender.setFactory(sentryClientFactory);
        return sentryAppender;
    }

    /**
     * Do some appender initialization *after* instance construction, so that we don't
     * log in the constructor (which can cause annoying messages) and so that system
     * properties and environment variables override hardcoded appender configuration.
     */
    @SuppressWarnings("checkstyle:hiddenfield")
    private void lazyInit() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    try {
                        String sentryClientFactory = Lookup.lookup("factory");
                        if (sentryClientFactory != null) {
                            setFactory(sentryClientFactory);
                        }

                        String release = Lookup.lookup("release");
                        if (release != null) {
                            setRelease(release);
                        }

                        String dist = Lookup.lookup("dist");
                        if (dist != null) {
                            setDist(dist);
                        }

                        String environment = Lookup.lookup("environment");
                        if (environment != null) {
                            setEnvironment(environment);
                        }

                        String serverName = Lookup.lookup("serverName");
                        if (serverName != null) {
                            setServerName(serverName);
                        }

                        String tags = Lookup.lookup("tags");
                        if (tags != null) {
                            setTags(tags);
                        }

                        String extraTags = Lookup.lookup("extraTags");
                        if (extraTags != null) {
                            setExtraTags(extraTags);
                        }
                    } finally {
                        initialized = true;
                    }
                }
            }
        }

        if (sentryClient == null) {
            initSentry();
        }
    }

    /**
     * Transforms a {@link Level} into an {@link Event.Level}.
     *
     * @param level original level as defined in log4j2.
     * @return log level used within sentry.
     */
    protected static Event.Level formatLevel(Level level) {
        if (level.isMoreSpecificThan(Level.FATAL)) {
            return Event.Level.FATAL;
        } else if (level.isMoreSpecificThan(Level.ERROR)) {
            return Event.Level.ERROR;
        } else if (level.isMoreSpecificThan(Level.WARN)) {
            return Event.Level.WARNING;
        } else if (level.isMoreSpecificThan(Level.INFO)) {
            return Event.Level.INFO;
        } else {
            return Event.Level.DEBUG;
        }
    }

    /**
     * Extracts message parameters into a List of Strings.
     * <p>
     * null parameters are kept as null.
     *
     * @param parameters parameters provided to the logging system.
     * @return the parameters formatted as Strings in a List.
     */
    protected static List<String> formatMessageParameters(Object[] parameters) {
        List<String> stringParameters = new ArrayList<>(parameters.length);
        for (Object parameter : parameters) {
            stringParameters.add((parameter != null) ? parameter.toString() : null);
        }
        return stringParameters;
    }

    @Override
    public void append(LogEvent logEvent) {
        // Do not log the event if the current thread is managed by sentry
        if (SentryEnvironment.isManagingThread()) {
            return;
        }

        SentryEnvironment.startManagingThread();
        try {
            lazyInit();
            Event event = buildEvent(logEvent);
            sentryClient.sendEvent(event);
        } catch (Exception e) {
            error("An exception occurred while creating a new event in Sentry", logEvent, e);
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }

    /**
     * Initialises the {@link SentryClient} instance.
     */
    protected synchronized void initSentry() {
        try {
            if (dsn == null) {
                dsn = Dsn.dsnLookup();
            }

            sentryClient = SentryClientFactory.sentryClient(new Dsn(dsn), sentryClientFactory);
        } catch (InvalidDsnException e) {
            error("An exception occurred during the retrieval of the DSN for Sentry", e);
        } catch (Exception e) {
            error("An exception occurred during the creation of a SentryClient instance", e);
        }
    }

    /**
     * Builds an Event based on the logging event.
     *
     * @param event Log generated.
     * @return Event containing details provided by the logging system.
     */
    protected Event buildEvent(LogEvent event) {
        Message eventMessage = event.getMessage();
        EventBuilder eventBuilder = new EventBuilder()
            .withSdkName(SentryEnvironment.SDK_NAME + ":log4j2")
            .withTimestamp(new Date(event.getTimeMillis()))
            .withMessage(eventMessage.getFormattedMessage())
            .withLogger(event.getLoggerName())
            .withLevel(formatLevel(event.getLevel()))
            .withExtra(THREAD_NAME, event.getThreadName());

        if (!Util.isNullOrEmpty(serverName)) {
            eventBuilder.withServerName(serverName.trim());
        }

        if (!Util.isNullOrEmpty(release)) {
            eventBuilder.withRelease(release.trim());
            if (!Util.isNullOrEmpty(dist)) {
                eventBuilder.withDist(dist.trim());
            }
        }

        if (!Util.isNullOrEmpty(environment)) {
            eventBuilder.withEnvironment(environment.trim());
        }

        if (eventMessage.getFormat() != null
            && !eventMessage.getFormat().equals("")
            && !eventMessage.getFormattedMessage().equals(eventMessage.getFormat())) {
            eventBuilder.withSentryInterface(new MessageInterface(
                eventMessage.getFormat(),
                formatMessageParameters(eventMessage.getParameters()),
                eventMessage.getFormattedMessage()));
        }

        Throwable throwable = event.getThrown();
        if (throwable != null) {
            eventBuilder.withSentryInterface(new ExceptionInterface(throwable));
        } else if (event.getSource() != null) {
            StackTraceElement[] stackTrace = {event.getSource()};
            eventBuilder.withSentryInterface(new StackTraceInterface(stackTrace));
        }

        if (event.getSource() != null) {
            eventBuilder.withCulprit(event.getSource());
        } else {
            eventBuilder.withCulprit(event.getLoggerName());
        }

        if (event.getContextStack() != null) {
            eventBuilder.withExtra(LOG4J_NDC, event.getContextStack().asList());
        }

        if (event.getContextMap() != null) {
            for (Map.Entry<String, String> contextEntry : event.getContextMap().entrySet()) {
                if (extraTags.contains(contextEntry.getKey())) {
                    eventBuilder.withTag(contextEntry.getKey(), contextEntry.getValue());
                } else {
                    eventBuilder.withExtra(contextEntry.getKey(), contextEntry.getValue());
                }
            }
        }

        if (event.getMarker() != null) {
            eventBuilder.withTag(LOG4J_MARKER, event.getMarker().getName());
        }

        for (Map.Entry<String, String> tagEntry : tags.entrySet()) {
            eventBuilder.withTag(tagEntry.getKey(), tagEntry.getValue());
        }

        sentryClient.runBuilderHelpers(eventBuilder);
        return eventBuilder.build();
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    public void setFactory(String factory) {
        this.sentryClientFactory = factory;
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
     * Set the tags that should be sent along with the events.
     *
     * @param tags A String of tags. key/values are separated by colon(:) and tags are separated by commas(,).
     */
    public void setTags(String tags) {
        this.tags = Util.parseTags(tags);
    }

    /**
     * Set the mapped extras that will be used to search the Thread Context Map and upgrade key pair to
     * a tag sent along with the events.
     *
     * @param extraTags A String of extraTags. extraTags are separated by commas(,).
     */
    public void setExtraTags(String extraTags) {
        this.extraTags = Util.parseExtraTags(extraTags);
    }

    @Override
    public void stop() {
        SentryEnvironment.startManagingThread();
        try {
            if (!isStarted()) {
                return;
            }
            super.stop();
            if (sentryClient != null) {
                sentryClient.closeConnection();
            }
        } catch (Exception e) {
            error("An exception occurred while closing the Sentry connection", e);
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }

    private class DropSentryFilter extends AbstractFilter {

        @Override
        public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
            return filter(logger.getName());
        }

        @Override
        public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
            return filter(logger.getName());
        }

        @Override
        public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
            return filter(logger.getName());
        }

        @Override
        public Result filter(LogEvent event) {
            return filter(event.getLoggerName());
        }

        private Result filter(String loggerName) {
            if (loggerName != null && loggerName.startsWith("io.sentry")) {
                return Result.DENY;
            }
            return Result.NEUTRAL;
        }
    }
}
