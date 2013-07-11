package net.kencochrane.raven.log4j2;

import net.kencochrane.raven.AbstractLoggerTest;
import net.kencochrane.raven.event.Event;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class SentryAppenderTest extends AbstractLoggerTest {
    private static final String LOGGER_NAME = SentryAppenderTest.class.getName();
    private SentryAppender sentryAppender;

    @Before
    public void setUp() throws Exception {
        sentryAppender = new SentryAppender(getMockRaven());
    }

    @Override
    public void logAnyLevel(String message) {
        logEvent(Level.INFO, null, null, message, null);
    }

    @Override
    public void logAnyLevel(String message, Throwable exception) {
        logEvent(Level.INFO, null, exception, message, null);
    }

    @Override
    public void logAnyLevel(String message, List<String> parameters) {
        logEvent(Level.INFO, null, null, message, parameters);
    }

    @Override
    public String getCurrentLoggerName() {
        return LOGGER_NAME;
    }

    @Override
    public String getUnformattedMessage() {
        return "Some content {} {} {}";
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
        logEvent(level, null, null, "", null);
        assertLogLevel(expectedLevel);
    }

    @Test
    public void testThreadNameAddedToExtra() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        logEvent(Level.INFO, null, null, "testMessage", null);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(),
                Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, Thread.currentThread().getName()));
    }

    @Test
    public void testMarkerAddedToTag() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        String markerName = UUID.randomUUID().toString();

        logEvent(Level.INFO, MarkerManager.getMarker(markerName), null, "testMessage", null);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTags(),
                Matchers.<String, Object>hasEntry(SentryAppender.LOG4J_MARKER, markerName));
    }

    @Test
    public void testMdcAddedToExtra() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        String extraKey = UUID.randomUUID().toString();
        String extraValue = UUID.randomUUID().toString();

        ThreadContext.put(extraKey, extraValue);

        logEvent(Level.INFO, null, null, "testMessage", null);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNdcAddedToExtra() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        String extra = UUID.randomUUID().toString();
        String extra2 = UUID.randomUUID().toString();

        ThreadContext.push(extra);
        logEvent(Level.INFO, null, null, "testMessage", null);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(), hasKey(SentryAppender.LOG4J_NDC));
        assertThat((Collection<String>) eventCaptor.getValue().getExtra().get(SentryAppender.LOG4J_NDC),
                contains(extra));

        reset(mockRaven);
        ThreadContext.push(extra2);
        logEvent(Level.INFO, null, null, "testMessage", null);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(), hasKey(SentryAppender.LOG4J_NDC));
        assertThat((Collection<String>) eventCaptor.getValue().getExtra().get(SentryAppender.LOG4J_NDC),
                contains(extra, extra2));
    }

    private void logEvent(Level level, Marker marker, Throwable exception, String messageString,
                          List<String> messageParameters) {
        Message message;
        if (messageParameters != null)
            message = new FormattedMessage(messageString, messageParameters.toArray());
        else
            message = new SimpleMessage(messageString);

        LogEvent event = new Log4jLogEvent(LOGGER_NAME, marker, SentryAppenderTest.class.getName(), level,
                message, exception);
        sentryAppender.append(event);
    }
}
