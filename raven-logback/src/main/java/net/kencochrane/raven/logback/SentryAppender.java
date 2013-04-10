package net.kencochrane.raven.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.LoggedEvent;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class SentryAppender extends AppenderBase<ILoggingEvent> {
    private final Raven raven;

    public SentryAppender() {
        this(new Raven());
    }

    public SentryAppender(Raven raven) {
        this.raven = raven;
    }

    private static List<String> formatArguments(Object[] argumentArray) {
        List<String> arguments = new ArrayList<String>(argumentArray.length);
        for (Object argument : argumentArray) {
            arguments.add(argument.toString());
        }
        return arguments;
    }

    private static LoggedEvent.Level formatLevel(ILoggingEvent iLoggingEvent) {
        if (iLoggingEvent.getLevel().isGreaterOrEqual(Level.ERROR)) {
            return LoggedEvent.Level.ERROR;
        } else if (iLoggingEvent.getLevel().isGreaterOrEqual(Level.WARN)) {
            return LoggedEvent.Level.WARNING;
        } else if (iLoggingEvent.getLevel().isGreaterOrEqual(Level.INFO)) {
            return LoggedEvent.Level.INFO;
        } else if (iLoggingEvent.getLevel().isGreaterOrEqual(Level.ALL)) {
            return LoggedEvent.Level.DEBUG;
        } else return null;
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        EventBuilder eventBuilder = new EventBuilder()
                .setTimestamp(new Date(iLoggingEvent.getTimeStamp()))
                .setMessage(iLoggingEvent.getFormattedMessage())
                .setLogger(iLoggingEvent.getLoggerName())
                .setLevel(formatLevel(iLoggingEvent))
                .setCulprit(iLoggingEvent.getLoggerName());

        if (iLoggingEvent.getThrowableProxy() != null) {
            Throwable throwable = ((ThrowableProxy) iLoggingEvent.getThrowableProxy()).getThrowable();
            eventBuilder.addSentryInterface(new ExceptionInterface(throwable))
                    .addSentryInterface(new StackTraceInterface(throwable));
        } else {
            if (iLoggingEvent.getArgumentArray() != null)
                eventBuilder.addSentryInterface(new MessageInterface(iLoggingEvent.getMessage(),
                        formatArguments(iLoggingEvent.getArgumentArray())));
            // When it's a message try to rely on the position of the log (the same message can be logged from
            // different places, or a same place can log a message in different ways.
            if (iLoggingEvent.getCallerData().length > 0)
                eventBuilder.generateChecksum(getEventPosition(iLoggingEvent));
        }

        for (Map.Entry<String, String> mdcEntry : iLoggingEvent.getMDCPropertyMap().entrySet()) {
            eventBuilder.addExtra(mdcEntry.getKey(), mdcEntry.getValue());
        }

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
}
