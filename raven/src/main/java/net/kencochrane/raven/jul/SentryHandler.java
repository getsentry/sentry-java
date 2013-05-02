package net.kencochrane.raven.jul;

import net.kencochrane.raven.Dsn;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class SentryHandler extends Handler {
    private final boolean propagateClose;
    private Raven raven;
    private String dsn;

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
            formattedParameters.add(parameter.toString());
        return formattedParameters;
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }

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

        if (record.getParameters() != null)
            eventBuilder.addSentryInterface(new MessageInterface(record.getMessage(),
                    formatParameters(record.getParameters())));
        else
            eventBuilder.setMessage(record.getMessage());

        getRaven().runBuilderHelpers(eventBuilder);

        getRaven().sendEvent(eventBuilder.build());
    }

    private Raven getRaven() {
        if (dsn == null)
            dsn = Dsn.dsnLookup();

        if (raven == null) {
            //TODO: Add a way to select the factory
            raven = RavenFactory.ravenInstance(new Dsn(dsn));
        }
        return raven;
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
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
            //TODO: What to do with that exception?
            e.printStackTrace();
        }
    }
}
