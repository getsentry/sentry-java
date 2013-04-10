package net.kencochrane.raven.log4j;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.LoggedEvent;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;

import java.util.Date;
import java.util.Map;
import java.util.Set;

public class SentryAppender extends AppenderSkeleton {
    private static final String LOG4J_NDC = "Log4J-NDC";
    private final Raven raven;

    public SentryAppender() {
        this(new Raven());
    }

    public SentryAppender(Raven raven) {
        this.raven = raven;
    }

    private static LoggedEvent.Level formatLevel(LoggingEvent loggingEvent) {
        if (loggingEvent.getLevel().isGreaterOrEqual(Level.FATAL)) {
            return LoggedEvent.Level.FATAL;
        } else if (loggingEvent.getLevel().isGreaterOrEqual(Level.ERROR)) {
            return LoggedEvent.Level.ERROR;
        } else if (loggingEvent.getLevel().isGreaterOrEqual(Level.WARN)) {
            return LoggedEvent.Level.WARNING;
        } else if (loggingEvent.getLevel().isGreaterOrEqual(Level.INFO)) {
            return LoggedEvent.Level.INFO;
        } else if (loggingEvent.getLevel().isGreaterOrEqual(Level.ALL)) {
            return LoggedEvent.Level.DEBUG;
        } else return null;
    }

    @Override
    protected void append(LoggingEvent loggingEvent) {
        EventBuilder eventBuilder = new EventBuilder()
                .setTimestamp(new Date(loggingEvent.getTimeStamp()))
                .setMessage(loggingEvent.getRenderedMessage())
                .setLogger(loggingEvent.getLoggerName())
                .setLevel(formatLevel(loggingEvent))
                .setCulprit(loggingEvent.getFQNOfLoggerClass());

        if (loggingEvent.getThrowableInformation() != null) {
            Throwable throwable = loggingEvent.getThrowableInformation().getThrowable();
            eventBuilder.addSentryInterface(new ExceptionInterface(throwable))
                    .addSentryInterface(new StackTraceInterface(throwable));
            eventBuilder.setCulprit(throwable);
        } else {
            // When it's a message try to rely on the position of the log (the same message can be logged from
            // different places, or a same place can log a message in different ways.
            if (loggingEvent.getLocationInformation().fullInfo != null)
                eventBuilder.generateChecksum(loggingEvent.getLocationInformation().fullInfo);
        }

        if (loggingEvent.getNDC() != null)
            eventBuilder.addExtra(LOG4J_NDC, loggingEvent.getNDC());

        for (Map.Entry mdcEntry : (Set<Map.Entry>) loggingEvent.getProperties().entrySet())
            eventBuilder.addExtra(mdcEntry.getKey().toString(), mdcEntry.getValue());

        raven.runBuilderHelpers(eventBuilder);

        raven.sendEvent(eventBuilder.build());
    }

    @Override
    public void close() {
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }
}
