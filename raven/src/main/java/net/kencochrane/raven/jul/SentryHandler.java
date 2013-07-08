package net.kencochrane.raven.jul;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.*;

/**
 * Logging handler in charge of sending the java.util.logging records to a Sentry server.
 */
public class SentryHandler extends Handler {
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
    private boolean guard = false;

    public SentryHandler() {
        propagateClose = true;
        retrieveProperties();
    }

    public SentryHandler(Raven raven) {
        this(raven, false);
    }

    public SentryHandler(Raven raven, boolean propagateClose) {
        this.raven = raven;
        this.propagateClose = propagateClose;
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
     * </p>
     *
     * @param parameters parameters provided to the logging system.
     * @return the parameters formatted as Strings in a List.
     */
    protected static List<String> formatMessageParameters(Object[] parameters) {
        List<String> formattedParameters = new ArrayList<String>(parameters.length);
        for (Object parameter : parameters)
            formattedParameters.add((parameter != null) ? parameter.toString() : null);
        return formattedParameters;
    }

    /**
     * Retrieves the properties of the logger.
     */
    protected void retrieveProperties() {
        LogManager manager = LogManager.getLogManager();
        dsn = manager.getProperty(SentryHandler.class.getName() + ".dsn");
        ravenFactory = manager.getProperty(SentryHandler.class.getName() + ".ravenFactory");
    }

    @Override
    public synchronized void publish(LogRecord record) {
        // Do not log the event if the current thread has been spawned by raven or if the event has been created during
        // the logging of an other event.
        if (!isLoggable(record) || Raven.RAVEN_THREAD.get() || guard)
            return;

        try {
            guard = true;
            if (raven == null)
                initRaven();
            Event event = buildEvent(record);
            raven.sendEvent(event);
        } finally {
            guard = false;
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
        } catch (Exception e) {
            reportError("An exception occurred during the creation of a raven instance", e, ErrorManager.OPEN_FAILURE);
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
                .setLevel(getLevel(record.getLevel()))
                .setTimestamp(new Date(record.getMillis()))
                .setLogger(record.getLoggerName());

        if (record.getSourceClassName() != null && record.getSourceMethodName() != null) {
            StackTraceElement fakeFrame = new StackTraceElement(record.getSourceClassName(),
                    record.getSourceMethodName(), null, -1);
            eventBuilder.setCulprit(fakeFrame);
        } else {
            eventBuilder.setCulprit(record.getLoggerName());
        }

        if (record.getThrown() != null) {
            eventBuilder.addSentryInterface(new ExceptionInterface(record.getThrown()));
        }

        if (record.getParameters() != null) {
            eventBuilder.addSentryInterface(new MessageInterface(record.getMessage(),
                    formatMessageParameters(record.getParameters())));
        } else {
            eventBuilder.setMessage(record.getMessage());
        }

        raven.runBuilderHelpers(eventBuilder);
        return eventBuilder.build();
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
        try {
            if (propagateClose)
                raven.getConnection().close();
        } catch (IOException e) {
            reportError("An exception occurred while closing the raven connection", e, ErrorManager.CLOSE_FAILURE);
        }
    }
}
