package net.kencochrane.raven.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import net.kencochrane.raven.AbstractLoggerTest;
import net.kencochrane.raven.event.Event;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class SentryAppenderTest extends AbstractLoggerTest {
    private Logger logger;

    @Before
    public void setUp() throws Exception {
        logger = new LoggerContext().getLogger(SentryAppenderTest.class);
        logger.setLevel(Level.ALL);
        Appender<ILoggingEvent> appender = new SentryAppender(getMockRaven());
        appender.start();
        logger.addAppender(appender);
    }

    @Override
    public void logAnyLevel(String message) {
        logger.info(message);
    }

    @Override
    public void logAnyLevel(String message, Throwable exception) {
        logger.error(message, exception);
    }

    @Override
    public void logAnyLevel(String message, List<String> parameters) {
        logger.info(message, parameters.toArray());
    }

    @Override
    public String getUnformattedMessage() {
        return "Some content {} {} {}";
    }

    @Override
    public String getCurrentLoggerName() {
        return logger.getName();
    }

    @Test
    @Override
    public void testLogLevelConversions() throws Exception {
        assertLevelConverted(Event.Level.DEBUG, Level.TRACE);
        assertLevelConverted(Event.Level.DEBUG, Level.DEBUG);
        assertLevelConverted(Event.Level.INFO, Level.INFO);
        assertLevelConverted(Event.Level.WARNING, Level.WARN);
        assertLevelConverted(Event.Level.ERROR, Level.ERROR);
    }

    private void assertLevelConverted(Event.Level expectedLevel, Level level) {
        if (level.isGreaterOrEqual(Level.ERROR)) {
            logger.error("message");
        } else if (level.isGreaterOrEqual(Level.WARN)) {
            logger.warn("message");
        } else if (level.isGreaterOrEqual(Level.INFO)) {
            logger.info("message");
        } else if (level.isGreaterOrEqual(Level.DEBUG)) {
            logger.debug("message");
        } else if (level.isGreaterOrEqual(Level.TRACE)) {
            logger.trace("message");
        }

        assertLogLevel(expectedLevel);
    }
}
