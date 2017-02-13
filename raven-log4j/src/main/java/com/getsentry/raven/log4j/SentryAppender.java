package com.getsentry.raven.log4j;

import com.getsentry.raven.Raven;
import com.getsentry.raven.RavenFactory;
import com.getsentry.raven.config.Lookup;
import com.getsentry.raven.dsn.Dsn;
import com.getsentry.raven.dsn.InvalidDsnException;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.interfaces.ExceptionInterface;
import com.getsentry.raven.event.interfaces.StackTraceInterface;
import com.getsentry.raven.util.Util;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * Appender for log4j in charge of sending the logged events to a Sentry server.
 */
public class SentryAppender extends AppenderSkeleton {
    /**
     * Name of the {@link Event#extra} property containing NDC details.
     */
    public static final String LOG4J_NDC = "log4J-NDC";
    /**
     * Name of the {@link Event#extra} property containing the Thread name.
     */
    public static final String THREAD_NAME = "Raven-Threadname";
    /**
     * Current instance of {@link Raven}.
     *
     * @see #initRaven()
     */
    protected volatile Raven raven;
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
     * Identifies the version of the application.
     * <p>
     * Might be null in which case the release information will not be sent with the event.
     */
    protected String release;
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
     * List of tags to look for in the MDC. These will be added as tags to be sent to sentry.
     * <p>
     * Might be empty in which case no mapped tags are set.
     */
    protected Set<String> extraTags = Collections.emptySet();

    /**
     * Creates an instance of SentryAppender.
     */
    public SentryAppender() {
        setRavenFactory(Lookup.lookup("ravenFactory"));
        setRelease(Lookup.lookup("release"));
        setEnvironment(Lookup.lookup("environment"));
        setServerName(Lookup.lookup("serverName"));
        setTags(Lookup.lookup("tags"));
        setExtraTags(Lookup.lookup("extraTags"));

        this.addFilter(new DropRavenFilter());
    }

    /**
     * Creates an instance of SentryAppender.
     *
     * @param raven instance of Raven to use with this appender.
     */
    public SentryAppender(Raven raven) {
        this();
        this.raven = raven;
    }

    /**
     * Transforms a {@link Level} into an {@link Event.Level}.
     *
     * @param level original level as defined in log4j.
     * @return log level used within raven.
     */
    protected static Event.Level formatLevel(Level level) {
        if (level.isGreaterOrEqual(Level.FATAL)) {
            return Event.Level.FATAL;
        } else if (level.isGreaterOrEqual(Level.ERROR)) {
            return Event.Level.ERROR;
        } else if (level.isGreaterOrEqual(Level.WARN)) {
            return Event.Level.WARNING;
        } else if (level.isGreaterOrEqual(Level.INFO)) {
            return Event.Level.INFO;
        } else if (level.isGreaterOrEqual(Level.ALL)) {
            return Event.Level.DEBUG;
        } else {
            return null;
        }
    }

    /**
     * Transforms the location info of a log into a stacktrace element (stackframe).
     *
     * @param location details on the location of the log.
     * @return a stackframe.
     */
    protected static StackTraceElement asStackTraceElement(LocationInfo location) {
        String fileName = (LocationInfo.NA.equals(location.getFileName())) ? null : location.getFileName();
        int line = (LocationInfo.NA.equals(location.getLineNumber())) ? -1 : Integer.parseInt(location.getLineNumber());
        return new StackTraceElement(location.getClassName(), location.getMethodName(), fileName, line);
    }

    @Override
    public void activateOptions() {
        super.activateOptions();

        if (raven == null) {
            initRaven();
        }
    }

    /**
     * Initialises the Raven instance.
     */
    protected synchronized void initRaven() {
        try {
            if (dsn == null) {
                dsn = Dsn.dsnLookup();
            }

            raven = RavenFactory.ravenInstance(new Dsn(dsn), ravenFactory);
        } catch (InvalidDsnException e) {
            getErrorHandler().error("An exception occurred during the retrieval of the DSN for Raven", e,
                    ErrorCode.ADDRESS_PARSE_FAILURE);
        } catch (Exception e) {
            getErrorHandler().error("An exception occurred during the creation of a Raven instance", e,
                    ErrorCode.FILE_OPEN_FAILURE);
        }
    }

    @Override
    protected void append(LoggingEvent loggingEvent) {
        // Do not log the event if the current thread is managed by raven
        if (RavenEnvironment.isManagingThread()) {
            return;
        }

        RavenEnvironment.startManagingThread();
        try {
            if (raven == null) {
                initRaven();
            }
            Event event = buildEvent(loggingEvent);
            raven.sendEvent(event);
        } catch (Exception e) {
            getErrorHandler().error("An exception occurred while creating a new event in Raven", e,
                    ErrorCode.WRITE_FAILURE);
        } finally {
            RavenEnvironment.stopManagingThread();
        }
    }

    /**
     * Builds an Event based on the logging event.
     *
     * @param loggingEvent Log generated.
     * @return Event containing details provided by the logging system.
     */
    protected Event buildEvent(LoggingEvent loggingEvent) {
        EventBuilder eventBuilder = new EventBuilder()
            .withSdkName(RavenEnvironment.SDK_NAME + ":log4j")
            .withTimestamp(new Date(loggingEvent.getTimeStamp()))
            .withMessage(loggingEvent.getRenderedMessage())
            .withLogger(loggingEvent.getLoggerName())
            .withLevel(formatLevel(loggingEvent.getLevel()))
            .withExtra(THREAD_NAME, loggingEvent.getThreadName());

        if (!Util.isNullOrEmpty(serverName)) {
            eventBuilder.withServerName(serverName.trim());
        }

        if (!Util.isNullOrEmpty(release)) {
            eventBuilder.withRelease(release.trim());
        }

        if (!Util.isNullOrEmpty(environment)) {
            eventBuilder.withEnvironment(environment.trim());
        }

        ThrowableInformation throwableInformation = null;
        try {
            throwableInformation = loggingEvent.getThrowableInformation();
        } catch (NullPointerException expected) {
            // `throwableInformation` is already set.
        }

        if (throwableInformation != null) {
            Throwable throwable = throwableInformation.getThrowable();
            eventBuilder.withSentryInterface(new ExceptionInterface(throwable));
        } else if (loggingEvent.getLocationInformation().fullInfo != null) {
            LocationInfo location = loggingEvent.getLocationInformation();
            if (!LocationInfo.NA.equals(location.getFileName()) && !LocationInfo.NA.equals(location.getLineNumber())) {
                StackTraceElement[] stackTrace = {asStackTraceElement(location)};
                eventBuilder.withSentryInterface(new StackTraceInterface(stackTrace));
            }
        }

        // Set culprit
        if (loggingEvent.getLocationInformation().fullInfo != null) {
            eventBuilder.withCulprit(asStackTraceElement(loggingEvent.getLocationInformation()));
        } else {
            eventBuilder.withCulprit(loggingEvent.getLoggerName());
        }

        if (loggingEvent.getNDC() != null) {
            eventBuilder.withExtra(LOG4J_NDC, loggingEvent.getNDC());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) loggingEvent.getProperties();
        for (Map.Entry<String, Object> mdcEntry : properties.entrySet()) {
            if (extraTags.contains(mdcEntry.getKey())) {
                eventBuilder.withTag(mdcEntry.getKey(), mdcEntry.getValue().toString());
            } else {
                eventBuilder.withExtra(mdcEntry.getKey(), mdcEntry.getValue());
            }
        }

        for (Map.Entry<String, String> tagEntry : tags.entrySet()) {
            eventBuilder.withTag(tagEntry.getKey(), tagEntry.getValue());
        }

        raven.runBuilderHelpers(eventBuilder);
        return eventBuilder.build();
    }

    public void setRavenFactory(String ravenFactory) {
        this.ravenFactory = ravenFactory;
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    public void setRelease(String release) {
        this.release = release;
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
     * Set the mapped extras that will be used to search MDC and upgrade key pair to a tag sent along with the events.
     *
     * @param extraTags A String of extraTags. extraTags are separated by commas(,).
     */
    public void setExtraTags(String extraTags) {
        this.extraTags = Util.parseExtraTags(extraTags);
    }

    @Override
    public void close() {
        RavenEnvironment.startManagingThread();
        try {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (raven != null) {
                raven.closeConnection();
            }
        } catch (Exception e) {
            getErrorHandler().error("An exception occurred while closing the Raven connection", e,
                    ErrorCode.CLOSE_FAILURE);
        } finally {
            RavenEnvironment.stopManagingThread();
        }
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

    private class DropRavenFilter extends Filter {
        @Override
        public int decide(LoggingEvent event) {
            String loggerName = event.getLoggerName();
            if (loggerName != null && loggerName.startsWith("com.getsentry.raven")) {
                return Filter.DENY;
            }
            return Filter.NEUTRAL;
        }
    }
}
