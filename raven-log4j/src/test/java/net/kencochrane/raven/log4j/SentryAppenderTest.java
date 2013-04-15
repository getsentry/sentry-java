package net.kencochrane.raven.log4j;

import net.kencochrane.raven.AbstractLoggerTest;
import net.kencochrane.raven.event.Event;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class SentryAppenderTest extends AbstractLoggerTest {
    private Logger logger;

    @Before
    public void setUp() throws Exception {
        logger = Logger.getLogger(SentryAppenderTest.class);
        logger.setLevel(Level.ALL);
        logger.setAdditivity(false);
        logger.addAppender(new SentryAppender(getMockRaven()));
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
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCurrentLoggerName() {
        return logger.getName();
    }

    @Override
    @Test
    public void testLogLevelConversions() throws Exception {
        assertLevelConverted(Event.Level.DEBUG, Level.TRACE);
        assertLevelConverted(Event.Level.DEBUG, Level.DEBUG);
        assertLevelConverted(Event.Level.INFO, Level.INFO);
        assertLevelConverted(Event.Level.WARNING, Level.WARN);
        assertLevelConverted(Event.Level.ERROR, Level.ERROR);
        assertLevelConverted(Event.Level.FATAL, Level.FATAL);
    }

    private void assertLevelConverted(Event.Level expectedLevel, Level level) {
        logger.log(level, null);
        assertLogLevel(expectedLevel);
    }

    @Override
    public void testLogParametrisedMessage() throws Exception {
        // Parametrised messages aren't supported
    }
}
