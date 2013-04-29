package net.kencochrane.raven.log4j2;

import net.kencochrane.raven.AbstractLoggerTest;
import net.kencochrane.raven.event.Event;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class SentryAppenderTest extends AbstractLoggerTest {
    private static final String LOGGER_NAME = SentryAppenderTest.class.getCanonicalName();
    private SentryAppender sentryAppender;

    @Before
    public void setUp() throws Exception {
        sentryAppender = new SentryAppender(getMockRaven());
    }

    @Override
    public void logAnyLevel(String message) {
        logEvent(Level.INFO, null, message, null);
    }

    @Override
    public void logAnyLevel(String message, Throwable exception) {
        logEvent(Level.INFO, exception, message, null);
    }

    @Override
    public void logAnyLevel(String message, List<String> parameters) {
        logEvent(Level.INFO, null, message, parameters);
    }

    @Override
    public String getCurrentLoggerName() {
        return LOGGER_NAME;
    }

    @Test
    @Override
    public void testLogLevelConversions() throws Exception {
        assertLevelConverted(Event.Level.DEBUG, Level.TRACE);
        assertLevelConverted(Event.Level.DEBUG, Level.DEBUG);
        assertLevelConverted(Event.Level.INFO, Level.INFO);
        assertLevelConverted(Event.Level.WARNING, Level.WARN);
        assertLevelConverted(Event.Level.ERROR, Level.ERROR);
        assertLevelConverted(Event.Level.FATAL, Level.FATAL);
    }

    private void assertLevelConverted(Event.Level expectedLevel, Level level) {
        logEvent(level, null, "", null);
        assertLogLevel(expectedLevel);
    }

    private void logEvent(Level level, Throwable exception, String messageString, List<String> messageParameters) {
        Message message;
        if (messageParameters != null)
            message = new FormattedMessage(messageString, messageParameters.toArray());
        else
            message = new SimpleMessage(messageString);

        LogEvent event = new Log4jLogEvent(LOGGER_NAME, null, SentryAppenderTest.class.getName(), level,
                message, exception);
        sentryAppender.append(event);
    }
}
