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
    private final boolean propagateClose;
    private Raven raven;
    private boolean guard = false;

    public SentryHandler() {
        propagateClose = true;
    }

    public SentryHandler(Raven raven) {
        this(raven, false);
    }

    public SentryHandler(Raven raven, boolean propagateClose) {
        this.raven = raven;
        this.propagateClose = propagateClose;
    }

    private static Event.Level getLevel(Level level) {
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

    private static List<String> formatParameters(Object[] parameters) {
        List<String> formattedParameters = new ArrayList<String>(parameters.length);
        for (Object parameter : parameters)
            formattedParameters.add((parameter != null) ? parameter.toString() : null);
        return formattedParameters;
    }

    @Override
    public synchronized void publish(LogRecord record) {
        // Do not log the event if the current thread has been spawned by raven or if the event has been created during
        // the logging of an other event.
        if (!isLoggable(record) || Raven.RAVEN_THREAD.get() || guard)
            return;

        try {
            guard = true;
            if (raven == null) {
                try {
                    start();
                } catch (Exception e) {
                    reportError("An exception occurred while creating an instance of raven", e, ErrorManager.OPEN_FAILURE);
                    return;
                }
            }

            raven.sendEvent(buildEvent(record));
        } finally {
            guard = false;
        }
    }

    private Event buildEvent(LogRecord record) {
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
                    formatParameters(record.getParameters())));
        } else {
            eventBuilder.setMessage(record.getMessage());
        }

        raven.runBuilderHelpers(eventBuilder);
        return eventBuilder.build();
    }

    private void start() {
        if (raven != null)
            return;

        LogManager manager = LogManager.getLogManager();
        String dsn = manager.getProperty(SentryHandler.class.getName() + ".dsn");
        String ravenFactory = manager.getProperty(SentryHandler.class.getName() + ".ravenFactory");

        if (dsn == null)
            dsn = Dsn.dsnLookup();

        raven = RavenFactory.ravenInstance(new Dsn(dsn), ravenFactory);
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
