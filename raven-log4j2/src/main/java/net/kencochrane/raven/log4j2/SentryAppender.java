package net.kencochrane.raven.log4j2;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
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
import org.apache.logging.log4j.core.config.plugins.PluginAttr;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.message.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Appender for log4j2 in charge of sending the logged events to a Sentry server.
 */
@Plugin(name = "Raven", category = "Core", elementType = "appender")
public class SentryAppender extends AbstractAppender<String> {
    /**
     * Default name for the appender.
     */
    public static final String APPENDER_NAME = "raven";
    /**
     * Name of the {@link Event#extra} property containing NDC details.
     */
    protected static final String LOG4J_NDC = "log4j2-NDC";
    /**
     * Name of the {@link Event#extra} property containing Marker details.
     */
    protected static final String LOG4J_MARKER = "log4j2-Marker";
    /**
     * Name of the {@link Event#extra} property containing the Thread name.
     */
    protected static final String THREAD_NAME = "Raven-Threadname";
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
    private final boolean propagateClose;

    public SentryAppender() {
        this(APPENDER_NAME, null, true);
    }


    public SentryAppender(Raven raven) {
        this(raven, false);
    }

    public SentryAppender(Raven raven, boolean propagateClose) {
        this(APPENDER_NAME, null, propagateClose);
        this.raven = raven;
    }

    private SentryAppender(String name, Filter filter, boolean propagateClose) {
        super(name, filter, null, true);
        this.propagateClose = propagateClose;
    }

    /**
     * Create a Sentry Appender.
     *
     * @param name         The name of the Appender.
     * @param dsn          Data Source Name to access the Sentry server.
     * @param ravenFactory Name of the factory to use to build the {@link Raven} instance.
     * @param filter       The filter, if any, to use.
     * @return The SentryAppender.
     */
    @PluginFactory
    public static SentryAppender createAppender(@PluginAttr("name") final String name,
                                                @PluginAttr("dsn") final String dsn,
                                                @PluginAttr("ravenFactory") final String ravenFactory,
                                                @PluginElement("filters") final Filter filter) {

        if (name == null) {
            LOGGER.error("No name provided for SentryAppender");
            return null;
        }

        SentryAppender sentryAppender = new SentryAppender(name, filter, true);
        sentryAppender.setDsn(dsn);
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
        switch (level) {
            case FATAL:
                return Event.Level.FATAL;
            case ERROR:
                return Event.Level.ERROR;
            case WARN:
                return Event.Level.WARNING;
            case INFO:
                return Event.Level.INFO;
            case DEBUG:
            case TRACE:
                return Event.Level.DEBUG;
            default:
                return null;
        }
    }

    /**
     * Gets the position of the event as a String.
     * <p>
     * Allows to generate a checksum when there is no stacktrace but the position of the log can be found.
     * </p>
     *
     * @param event event without stacktrace but with a position.
     * @return a string version of the position.
     */
    protected static String getEventPosition(LogEvent event) {
        StackTraceElement stackTraceElement = event.getSource();
        return stackTraceElement.getClassName() + stackTraceElement.getMethodName() + stackTraceElement.getLineNumber();
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
        List<String> stringParameters = new ArrayList<String>(parameters.length);
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

        // Do not log the event if the current thread has been spawned by raven
        if (Raven.RAVEN_THREAD.get())
            return;

        if (raven == null)
            initRaven();
        Event event = buildEvent(logEvent);
        raven.sendEvent(event);
    }

    /**
     * Initialises the Raven instance.
     */
    protected void initRaven() {
        try {
            if (dsn == null)
                dsn = Dsn.dsnLookup();

            raven = RavenFactory.ravenInstance(new Dsn(dsn), ravenFactory);
        } catch (Exception e) {
            error("An exception occurred during the creation of a raven instance", e);
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

        if (event.getThrown() != null) {
            Throwable throwable = event.getThrown();
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

        raven.runBuilderHelpers(eventBuilder);
        return eventBuilder.build();
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    public void setRavenFactory(String ravenFactory) {
        this.ravenFactory = ravenFactory;
    }

    @Override
    public void stop() {
        super.stop();

        try {
            if (propagateClose && raven != null)
                raven.getConnection().close();
        } catch (IOException e) {
            error("An exception occurred while closing the raven connection", e);
        }
    }
}
