package io.sentry.jul;

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
import io.sentry.util.Util;
import org.slf4j.MDC;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * Logging handler in charge of sending the java.util.logging records to a Sentry server.
 */
public class SentryHandler extends Handler {
    /**
     * Name of the {@link Event#extra} property containing the Thread id.
     */
    public static final String THREAD_ID = "Sentry-ThreadId";
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
     * If true, <code>String.format()</code> is used to render parameterized log
     * messages instead of <code>MessageFormat.format()</code>; Defaults to
     * false.
     */
    protected boolean printfStyle;
    /**
     * Name of the {@link SentryClientFactory} being used.
     * <p>
     * Might be null in which case the factory should be defined automatically.
     */
    protected String sentryFactory;
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
     * Tags to add to every event.
     */
    protected Map<String, String> tags = Collections.emptyMap();
    /**
     * Set of tags to look for in the MDC. These will be added as tags to be sent to sentry.
     * <p>
     * Might be empty in which case no mapped tags are set.
     */
    protected Set<String> extraTags = Collections.emptySet();
    /**
     * Used for lazy initialization of appender state, see {@link #lazyInit()}.
     */
    private volatile boolean initialized = false;

    /**
     * Creates an instance of SentryHandler.
     */
    public SentryHandler() {
        retrieveProperties();
        this.setFilter(new DropSentryFilter());
    }

    /**
     * Creates an instance of SentryHandler.
     *
     * @param sentryClient instance of Sentry to use with this appender.
     */
    public SentryHandler(SentryClient sentryClient) {
        this();
        this.sentryClient = sentryClient;
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
                        String sentryFactory = Lookup.lookup("sentryFactory");
                        if (sentryFactory != null) {
                            setSentryFactory(sentryFactory);
                        }

                        String release = Lookup.lookup("release");
                        if (release != null) {
                            setRelease(release);
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
     * @param level original level as defined in JUL.
     * @return log level used within sentry.
     */
    protected static Event.Level getLevel(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return Event.Level.ERROR;
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            return Event.Level.WARNING;
        } else if (level.intValue() >= Level.INFO.intValue()) {
            return Event.Level.INFO;
        } else if (level.intValue() >= Level.ALL.intValue()) {
            return Event.Level.DEBUG;
        } else {
            return null;
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
        List<String> formattedParameters = new ArrayList<>(parameters.length);
        for (Object parameter : parameters) {
            formattedParameters.add((parameter != null) ? parameter.toString() : null);
        }
        return formattedParameters;
    }

    /**
     * Retrieves the properties of the logger.
     */
    protected void retrieveProperties() {
        LogManager manager = LogManager.getLogManager();
        String className = SentryHandler.class.getName();
        String dsnProperty = manager.getProperty(className + ".dsn");
        if (dsnProperty != null) {
            setDsn(dsnProperty);
        }
        String sentryFactoryProperty = manager.getProperty(className + ".sentryFactory");
        if (sentryFactoryProperty != null) {
            setSentryFactory(sentryFactoryProperty);
        }
        String releaseProperty = manager.getProperty(className + ".release");
        if (releaseProperty != null) {
            setRelease(releaseProperty);
        }
        String environmentProperty = manager.getProperty(className + ".environment");
        if (environmentProperty != null) {
            setEnvironment(environmentProperty);
        }
        String serverNameProperty = manager.getProperty(className + ".serverName");
        if (serverNameProperty != null) {
            setServerName(serverNameProperty);
        }
        String tagsProperty = manager.getProperty(className + ".tags");
        if (tagsProperty != null) {
            setTags(tagsProperty);
        }
        String extraTagsProperty = manager.getProperty(className + ".extraTags");
        if (extraTagsProperty != null) {
            setExtraTags(extraTagsProperty);
        }
        setPrintfStyle(Boolean.valueOf(manager.getProperty(className + ".printfStyle")));
    }

    @Override
    public void publish(LogRecord record) {
        // Do not log the event if the current thread is managed by sentry
        if (!isLoggable(record) || SentryEnvironment.isManagingThread()) {
            return;
        }

        SentryEnvironment.startManagingThread();
        try {
            lazyInit();
            Event event = buildEvent(record);
            sentryClient.sendEvent(event);
        } catch (Exception e) {
            reportError("An exception occurred while creating a new event in Sentry", e, ErrorManager.WRITE_FAILURE);
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }

    /**
     * Initialises the Sentry instance.
     */
    protected synchronized void initSentry() {
        try {
            if (dsn == null) {
                dsn = Dsn.dsnLookup();
            }

            sentryClient = SentryClientFactory.sentryClient(new Dsn(dsn), sentryFactory);
        } catch (InvalidDsnException e) {
            reportError("An exception occurred during the retrieval of the DSN for Sentry",
                e, ErrorManager.OPEN_FAILURE);
        } catch (Exception e) {
            reportError("An exception occurred during the creation of a Sentry instance", e, ErrorManager.OPEN_FAILURE);
        }
    }

    /**
     * Builds an Event based on the log record.
     *
     * @param record Log generated.
     * @return Event containing details provided by the logging system.
     */
    protected Event buildEvent(LogRecord record) {
        EventBuilder eventBuilder = new EventBuilder()
            .withSdkName(SentryEnvironment.SDK_NAME + ":jul")
            .withLevel(getLevel(record.getLevel()))
            .withTimestamp(new Date(record.getMillis()))
            .withLogger(record.getLoggerName());

        String message = record.getMessage();
        if (record.getResourceBundle() != null && record.getResourceBundle().containsKey(record.getMessage())) {
            message = record.getResourceBundle().getString(record.getMessage());
        }

        String topLevelMessage = message;
        if (record.getParameters() == null) {
            eventBuilder.withSentryInterface(new MessageInterface(message));
        } else {
            String formatted;
            List<String> parameters = formatMessageParameters(record.getParameters());
            try {
                formatted = formatMessage(message, record.getParameters());
                topLevelMessage = formatted; // write out formatted as Event's message key
            } catch (Exception e) {
                // local formatting failed, send message and parameters without formatted string
                formatted = null;
            }
            eventBuilder.withSentryInterface(new MessageInterface(message, parameters, formatted));
        }
        eventBuilder.withMessage(topLevelMessage);

        Throwable throwable = record.getThrown();
        if (throwable != null) {
            eventBuilder.withSentryInterface(new ExceptionInterface(throwable));
        }

        if (record.getSourceClassName() != null && record.getSourceMethodName() != null) {
            StackTraceElement fakeFrame = new StackTraceElement(record.getSourceClassName(),
                record.getSourceMethodName(), null, -1);
            eventBuilder.withCulprit(fakeFrame);
        } else {
            eventBuilder.withCulprit(record.getLoggerName());
        }

        Map<String, String> mdc = MDC.getMDCAdapter().getCopyOfContextMap();
        if (mdc != null) {
            for (Map.Entry<String, String> mdcEntry : mdc.entrySet()) {
                if (extraTags.contains(mdcEntry.getKey())) {
                    eventBuilder.withTag(mdcEntry.getKey(), mdcEntry.getValue());
                } else {
                    eventBuilder.withExtra(mdcEntry.getKey(), mdcEntry.getValue());
                }
            }
        }

        for (Map.Entry<String, String> tagEntry : tags.entrySet()) {
            eventBuilder.withTag(tagEntry.getKey(), tagEntry.getValue());
        }

        eventBuilder.withExtra(THREAD_ID, record.getThreadID());

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

        sentryClient.runBuilderHelpers(eventBuilder);
        return eventBuilder.build();
    }

    /**
     * Returns formatted Event message when provided the message template and
     * parameters.
     *
     * @param message Message template body.
     * @param parameters Array of parameters for the message.
     * @return Formatted message.
     */
    protected String formatMessage(String message, Object[] parameters) {
        String formatted;
        if (printfStyle) {
            formatted = String.format(message, parameters);
        } else {
            formatted = MessageFormat.format(message, parameters);
        }
        return formatted;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
        SentryEnvironment.startManagingThread();
        try {
            if (sentryClient != null) {
                sentryClient.closeConnection();
            }
        } catch (Exception e) {
            reportError("An exception occurred while closing the Sentry connection", e, ErrorManager.CLOSE_FAILURE);
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    public void setPrintfStyle(boolean printfStyle) {
        this.printfStyle = printfStyle;
    }

    public void setSentryFactory(String sentryFactory) {
        this.sentryFactory = sentryFactory;
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
     * Populates the tags map by parsing the given tags property string.
     * @param tags comma-delimited key-value pairs, e.g.
     *                     "tag1:value1,tag2:value2".
     */
    public void setTags(String tags) {
        this.tags = Util.parseTags(tags);
    }

    public void setExtraTags(String extraTags) {
        this.extraTags = Util.parseExtraTags(extraTags);
    }

    private class DropSentryFilter implements Filter {
        @Override
        public boolean isLoggable(LogRecord record) {
            String loggerName = record.getLoggerName();
            return loggerName == null || !loggerName.startsWith("io.sentry");
        }
    }
}
