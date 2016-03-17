package com.getsentry.raven.jul;

import com.google.common.base.Splitter;
import com.getsentry.raven.Raven;
import com.getsentry.raven.RavenFactory;
import com.getsentry.raven.dsn.Dsn;
import com.getsentry.raven.dsn.InvalidDsnException;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.interfaces.ExceptionInterface;
import com.getsentry.raven.event.interfaces.MessageInterface;
import com.google.common.base.Strings;
import org.slf4j.MDC;

import java.text.MessageFormat;
import java.util.*;
import java.util.logging.*;

/**
 * Logging handler in charge of sending the java.util.logging records to a Sentry server.
 */
public class SentryHandler extends Handler {
    /**
     * Name of the {@link Event#extra} property containing the Thread id.
     */
    public static final String THREAD_ID = "Raven-ThreadId";
    /**
     * Current instance of {@link Raven}.
     *
     * @see #initRaven()
     */
    protected Raven raven;
    /**
     * DSN property of the appender.
     * <p>
     * Might be null in which case the DSN should be detected automatically.
     */
    protected String dsn;
    /**
     * Name of the {@link RavenFactory} being used.
     * <p>
     * Might be null in which case the factory should be defined automatically.
     */
    protected String ravenFactory;
    /**
     * Release to be sent to sentry.
     * <p>
     * Might be null in which case no release is sent.
     */
    protected String release;
    /**
     * Tags to add to every event.
     */
    protected Map<String, String> tags = Collections.emptyMap();

    /**
     * Set of tags to look for in the MDC. These will be added as tags to be sent to sentry.
     * <p>
     * Might be empty in which case no mapped tags are set.
     */
    private Set<String> extraTags = Collections.emptySet();

    /**
     * Creates an instance of SentryHandler.
     */
    public SentryHandler() {
        retrieveProperties();
    }

    /**
     * Creates an instance of SentryHandler.
     *
     * @param raven instance of Raven to use with this appender.
     */
    public SentryHandler(Raven raven) {
        this.raven = raven;
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    /**
     * Populates the tags map by parsing the given tags property string.
     * @param tagsProperty comma-delimited key-value pairs, e.g.
     *                     "tag1:value1,tag2:value2".
     */
    public void setTags(String tagsProperty) {
        this.tags = parseTags(tagsProperty);
    }

    private Map<String, String> parseTags(String tagsProperty) {
        return tagsProperty == null ? Collections.<String, String>emptyMap()
                : Splitter.on(",").withKeyValueSeparator(":").split(tagsProperty);
    }

    /**
     * Transforms a {@link Level} into an {@link Event.Level}.
     *
     * @param level original level as defined in JUL.
     * @return log level used within raven.
     */
    protected static Event.Level getLevel(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue())
            return Event.Level.ERROR;
        else if (level.intValue() >= Level.WARNING.intValue())
            return Event.Level.WARNING;
        else if (level.intValue() >= Level.INFO.intValue())
            return Event.Level.INFO;
        else if (level.intValue() >= Level.ALL.intValue())
            return Event.Level.DEBUG;
        else return null;
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
        for (Object parameter : parameters)
            formattedParameters.add((parameter != null) ? parameter.toString() : null);
        return formattedParameters;
    }

    /**
     * Retrieves the properties of the logger.
     */
    protected void retrieveProperties() {
        LogManager manager = LogManager.getLogManager();
        String className = SentryHandler.class.getName();
        dsn = manager.getProperty(className + ".dsn");
        ravenFactory = manager.getProperty(className + ".ravenFactory");
        release = manager.getProperty(className + ".release");
        String tagsProperty = manager.getProperty(className + ".tags");
        tags = parseTags(tagsProperty);
        String extraTagsProperty = manager.getProperty(className + ".extraTags");
        if (extraTagsProperty != null)
            extraTags = new HashSet<>(Arrays.asList(extraTagsProperty.split(",")));
    }

    @Override
    public void publish(LogRecord record) {
        // Do not log the event if the current thread is managed by raven
        if (!isLoggable(record) || RavenEnvironment.isManagingThread())
            return;

        RavenEnvironment.startManagingThread();
        try {
            if (raven == null)
                initRaven();
            Event event = buildEvent(record);
            raven.sendEvent(event);
        } catch (Exception e) {
            reportError("An exception occurred while creating a new event in Raven", e, ErrorManager.WRITE_FAILURE);
        } finally {
            RavenEnvironment.stopManagingThread();
        }
    }

    /**
     * Initialises the Raven instance.
     */
    protected void initRaven() {
        try {
            if (dsn == null)
                dsn = Dsn.dsnLookup();

            raven = RavenFactory.ravenInstance(new Dsn(dsn), ravenFactory);
        } catch (InvalidDsnException e) {
            reportError("An exception occurred during the retrieval of the DSN for Raven",
                    e, ErrorManager.OPEN_FAILURE);
        } catch (Exception e) {
            reportError("An exception occurred during the creation of a Raven instance", e, ErrorManager.OPEN_FAILURE);
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
                .withLevel(getLevel(record.getLevel()))
                .withTimestamp(new Date(record.getMillis()))
                .withLogger(record.getLoggerName());

        String message = record.getMessage();
        if (record.getResourceBundle() != null && record.getResourceBundle().containsKey(record.getMessage())) {
            message = record.getResourceBundle().getString(record.getMessage());
        }
        if (record.getParameters() != null) {
            List<String> parameters = formatMessageParameters(record.getParameters());
            eventBuilder.withSentryInterface(new MessageInterface(message, parameters));
            message = MessageFormat.format(message, record.getParameters());
        }
        eventBuilder.withMessage(message);

        Throwable throwable = record.getThrown();
        if (throwable != null)
            eventBuilder.withSentryInterface(new ExceptionInterface(throwable));

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

        if (!Strings.isNullOrEmpty(release)) {
            eventBuilder.withRelease(release.trim());
        }

        raven.runBuilderHelpers(eventBuilder);
        return eventBuilder.build();
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
        RavenEnvironment.startManagingThread();
        try {
            if (raven != null)
                raven.closeConnection();
        } catch (Exception e) {
            reportError("An exception occurred while closing the Raven connection", e, ErrorManager.CLOSE_FAILURE);
        } finally {
            RavenEnvironment.stopManagingThread();
        }
    }

    public void setRelease(String release) {
        this.release = release;
    }
}
