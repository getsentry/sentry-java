package net.kencochrane.raven.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import net.kencochrane.raven.AbstractLoggerTest;
import net.kencochrane.raven.event.Event;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MarkerFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;

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

    @Test
    public void testThreadNameAddedToExtra() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        logger.info("testMessage");

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(),
                Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, Thread.currentThread().getName()));
    }

    @Test
    public void testMarkerAddedToTag() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        String markerName = UUID.randomUUID().toString();

        logger.info(MarkerFactory.getMarker(markerName), "testMessage");

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTags(),
                Matchers.<String, Object>hasEntry(SentryAppender.LOGBACK_MARKER, markerName));
    }
}
