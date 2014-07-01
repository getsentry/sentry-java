package net.kencochrane.raven.log4j2;

import com.google.common.base.Splitter;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.dsn.InvalidDsnException;
import net.kencochrane.raven.environment.RavenEnvironment;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.message.Message;

import java.util.*;

/**
 * Appender for log4j2 in charge of sending the logged events to a Sentry server.
 */
@Plugin(name = "Raven", category = "Core", elementType = "appender", printObject = true)
public class SentryAppender extends AbstractAppender {
    /**
     * Default name for the appender.
     */
    public static final String APPENDER_NAME = "raven";
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
    public static final String THREAD_NAME = "Raven-Threadname";
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
     * </p>
     */
    protected String dsn;
    /**
     * Name of the {@link RavenFactory} being used.
     * <p>
     * Might be null in which case the factory should be defined automatically.
     * </p>
     */
    protected String ravenFactory;
    /**
     * Additional tags to be sent to sentry.
     * <p>
     * Might be empty in which case no tags are sent.
     * </p>
     */
    protected Map<String, String> tags = Collections.emptyMap();

    /**
     * Creates an instance of SentryAppender.
     */
    public SentryAppender() {
        this(APPENDER_NAME, null);
    }

    /**
     * Creates an instance of SentryAppender.
     *
     * @param raven instance of Raven to use with this appender.
     */
    public SentryAppender(Raven raven) {
        this(APPENDER_NAME, null);
        this.raven = raven;
    }

    private SentryAppender(String name, Filter filter) {
        super(name, filter, null, true);
    }

    /**
     * Create a Sentry Appender.
     *
     * @param name         The name of the Appender.
     * @param dsn          Data Source Name to access the Sentry server.
     * @param ravenFactory Name of the factory to use to build the {@link Raven} instance.
     * @param tags         Tags to add to each event.
     * @param filter       The filter, if any, to use.
     * @return The SentryAppender.
     */
    @PluginFactory
    public static SentryAppender createAppender(@PluginAttribute("name") final String name,
                                                @PluginAttribute("dsn") final String dsn,
                                                @PluginAttribute("ravenFactory") final String ravenFactory,
                                                @PluginAttribute("tags") final String tags,
                                                @PluginElement("filters") final Filter filter) {

        if (name == null) {
            LOGGER.error("No name provided for SentryAppender");
            return null;
        }

        SentryAppender sentryAppender = new SentryAppender(name, filter);
        sentryAppender.setDsn(dsn);
        if (tags != null)
            sentryAppender.setTags(tags);
        sentryAppender.setRavenFactory(ravenFactory);
        return sentryAppender;
    }

    /**
     * Transforms a {@link Level} into an {@link Event.Level}.
     *
     * @param level original level as defined in log4j2.
     * @return log level used within raven.
     */
    protected static Event.Level formatLevel(Level level) {
        if (level.isAtLeastAsSpecificAs(Level.FATAL))
            return Event.Level.FATAL;
        else if (level.isAtLeastAsSpecificAs(Level.ERROR))
            return Event.Level.ERROR;
        else if (level.isAtLeastAsSpecificAs(Level.WARN))
            return Event.Level.WARNING;
        else if (level.isAtLeastAsSpecificAs(Level.INFO))
            return Event.Level.INFO;
        else
            return Event.Level.DEBUG;
    }

    /**
     * Extracts message parameters into a List of Strings.
     * <p>
     * null parameters are kept as null.
     * </p>
     *
     * @param parameters parameters provided to the logging system.
     * @return the parameters formatted as Strings in a List.
     */
    protected static List<String> formatMessageParameters(Object[] parameters) {
        List<String> stringParameters = new ArrayList<>(parameters.length);
        for (Object parameter : parameters)
            stringParameters.add((parameter != null) ? parameter.toString() : null);
        return stringParameters;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The raven instance is set in this method instead of {@link #start()} in order to avoid substitute loggers
     * being generated during the instantiation of {@link Raven}.<br />
     * </p>
     *
     * @param logEvent The LogEvent.
     */
    @Override
    public void append(LogEvent logEvent) {
        // Do not log the event if the current thread is managed by raven
        if (RavenEnvironment.isManagingThread())
            return;

        RavenEnvironment.startManagingThread();
        try {
            if (raven == null)
                initRaven();

            Event event = buildEvent(logEvent);
            raven.sendEvent(event);
        } catch (Exception e) {
            error("An exception occurred while creating a new event in Raven", logEvent, e);
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
            error("An exception occurred during the retrieval of the DSN for Raven", e);
        } catch (Exception e) {
            error("An exception occurred during the creation of a Raven instance", e);
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
                .setTimestamp(new Date(event.getMillis()))
                .setMessage(eventMessage.getFormattedMessage())
                .setLogger(event.getLoggerName())
                .setLevel(formatLevel(event.getLevel()))
                .addExtra(THREAD_NAME, event.getThreadName());

        if (!eventMessage.getFormattedMessage().equals(eventMessage.getFormat())) {
            eventBuilder.addSentryInterface(new MessageInterface(eventMessage.getFormat(),
                    formatMessageParameters(eventMessage.getParameters())));
        }

        Throwable throwable = event.getThrown();
        if (throwable != null) {
            eventBuilder.addSentryInterface(new ExceptionInterface(throwable));
        } else if (event.getSource() != null) {
            StackTraceElement[] stackTrace = {event.getSource()};
            eventBuilder.addSentryInterface(new StackTraceInterface(stackTrace));
        }

        if (event.getSource() != null) {
            eventBuilder.setCulprit(event.getSource());
        } else {
            eventBuilder.setCulprit(event.getLoggerName());
        }

        if (event.getContextStack() != null)
            eventBuilder.addExtra(LOG4J_NDC, event.getContextStack().asList());

        if (event.getContextMap() != null) {
            for (Map.Entry<String, String> mdcEntry : event.getContextMap().entrySet()) {
                eventBuilder.addExtra(mdcEntry.getKey(), mdcEntry.getValue());
            }
        }

        if (event.getMarker() != null)
            eventBuilder.addTag(LOG4J_MARKER, event.getMarker().getName());

        for (Map.Entry<String, String> tagEntry : tags.entrySet())
            eventBuilder.addTag(tagEntry.getKey(), tagEntry.getValue());

        raven.runBuilderHelpers(eventBuilder);
        return eventBuilder.build();
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    public void setRavenFactory(String ravenFactory) {
        this.ravenFactory = ravenFactory;
    }

    /**
     * Set the tags that should be sent along with the events.
     *
     * @param tags A String of tags. key/values are separated by colon(:) and tags are separated by commas(,).
     */
    public void setTags(String tags) {
        this.tags = Splitter.on(",").withKeyValueSeparator(":").split(tags);
    }

    @Override
    public void stop() {
        RavenEnvironment.startManagingThread();
        try {
            if (!isStarted())
                return;
            super.stop();
            if (raven != null)
                raven.closeConnection();
        } catch (Exception e) {
            error("An exception occurred while closing the Raven connection", e);
        } finally {
            RavenEnvironment.stopManagingThread();
        }
    }
}
