package net.kencochrane.raven.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
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
import java.util.Map;

/**
 * Appender for logback in charge of sending the logged events to a Sentry server.
 */
public class SentryAppender extends AppenderBase<ILoggingEvent> {
    private final boolean propagateClose;
    private Raven raven;
    private String dsn;
    private String ravenFactory;

    public SentryAppender() {
        propagateClose = true;
    }

    public SentryAppender(Raven raven) {
        this(raven, false);
    }

    public SentryAppender(Raven raven, boolean propagateClose) {
        this.raven = raven;
        this.propagateClose = propagateClose;
    }

    private static List<String> formatArguments(Object[] argumentArray) {
        List<String> arguments = new ArrayList<String>(argumentArray.length);
        for (Object argument : argumentArray) {
            arguments.add(argument.toString());
        }
        return arguments;
    }

    private static Event.Level formatLevel(ILoggingEvent iLoggingEvent) {
        if (iLoggingEvent.getLevel().isGreaterOrEqual(Level.ERROR)) {
            return Event.Level.ERROR;
        } else if (iLoggingEvent.getLevel().isGreaterOrEqual(Level.WARN)) {
            return Event.Level.WARNING;
        } else if (iLoggingEvent.getLevel().isGreaterOrEqual(Level.INFO)) {
            return Event.Level.INFO;
        } else if (iLoggingEvent.getLevel().isGreaterOrEqual(Level.ALL)) {
            return Event.Level.DEBUG;
        } else return null;
    }

    @Override
    public void start() {
        super.start();

        if (raven == null) {
            if (dsn == null)
                dsn = Dsn.dsnLookup();

            raven = RavenFactory.ravenInstance(new Dsn(dsn), ravenFactory);
        }
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        EventBuilder eventBuilder = new EventBuilder()
                .setTimestamp(new Date(iLoggingEvent.getTimeStamp()))
                .setMessage(iLoggingEvent.getFormattedMessage())
                .setLogger(iLoggingEvent.getLoggerName())
                .setLevel(formatLevel(iLoggingEvent));

        if (iLoggingEvent.getThrowableProxy() != null) {
            Throwable throwable = ((ThrowableProxy) iLoggingEvent.getThrowableProxy()).getThrowable();
            eventBuilder.addSentryInterface(new ExceptionInterface(throwable));
        } else {
            if (iLoggingEvent.getArgumentArray() != null)
                eventBuilder.addSentryInterface(new MessageInterface(iLoggingEvent.getMessage(),
                        formatArguments(iLoggingEvent.getArgumentArray())));
            // When it's a message try to rely on the position of the log (the same message can be logged from
            // different places, or a same place can log a message in different ways.
            if (iLoggingEvent.getCallerData().length > 0) {
                eventBuilder.generateChecksum(getEventPosition(iLoggingEvent));
            }
        }

        if (iLoggingEvent.getCallerData().length > 0) {
            eventBuilder.setCulprit(iLoggingEvent.getCallerData()[0]);
        } else {
            eventBuilder.setCulprit(iLoggingEvent.getLoggerName());
        }

        for (Map.Entry<String, String> mdcEntry : iLoggingEvent.getMDCPropertyMap().entrySet()) {
            eventBuilder.addExtra(mdcEntry.getKey(), mdcEntry.getValue());
        }

        raven.runBuilderHelpers(eventBuilder);

        raven.sendEvent(eventBuilder.build());
    }

    private String getEventPosition(ILoggingEvent iLoggingEvent) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement stackTraceElement : iLoggingEvent.getCallerData()) {
            sb.append(stackTraceElement.getClassName())
                    .append(stackTraceElement.getMethodName())
                    .append(stackTraceElement.getLineNumber());
        }
        return sb.toString();
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
            if (propagateClose)
                raven.getConnection().close();
        } catch (IOException e) {
            addError("An exception occurred while closing the raven connection", e);
        }
    }
}
