package net.kencochrane.raven.log4j2;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

public class SentryAppender extends AbstractAppender<Serializable> {
    public static final String APPENDER_NAME = "raven";
    private static final String LOG4J_NDC = "Log4J-NDC";
    private final Raven raven;

    public SentryAppender() {
        this(new Raven());
    }

    public SentryAppender(Raven raven) {
        super(APPENDER_NAME, null, null);
        this.raven = raven;
    }

    private static Event.Level formatLevel(Level level) {
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

    @Override
    public void append(LogEvent event) {
        EventBuilder eventBuilder = new EventBuilder()
                .setTimestamp(new Date(event.getMillis()))
                .setMessage(event.getMessage().getFormattedMessage())
                .setLogger(event.getLoggerName())
                .setLevel(formatLevel(event.getLevel()))
                .setCulprit(formatCulprit(event.getSource()));

        if (event.getThrown() != null) {
            Throwable throwable = event.getThrown();
            eventBuilder.addSentryInterface(new ExceptionInterface(throwable))
                    .addSentryInterface(new StackTraceInterface(throwable));
            eventBuilder.setCulprit(throwable);
        } else {
            // When it's a message try to rely on the position of the log (the same message can be logged from
            // different places, or a same place can log a message in different ways.
            eventBuilder.generateChecksum(formatCulprit(event.getSource()));
        }

        if (event.getContextStack() != null) {
            eventBuilder.addExtra(LOG4J_NDC, event.getContextStack().asList());
        }

        if (event.getContextMap() != null) {
            for (Map.Entry<String, String> mdcEntry : event.getContextMap().entrySet()) {
                eventBuilder.addExtra(mdcEntry.getKey(), mdcEntry.getValue());
            }
        }

        raven.runBuilderHelpers(eventBuilder);

        raven.sendEvent(eventBuilder.build());
    }

    private String formatCulprit(StackTraceElement stackTraceElement) {
        return stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName()
                + " at line " + stackTraceElement.getLineNumber();
    }
}
