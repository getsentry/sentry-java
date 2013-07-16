package net.kencochrane.raven.log4j;

import net.kencochrane.raven.AbstractLoggerTest;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import org.apache.log4j.*;
import org.apache.log4j.helpers.OnlyOnceErrorHandler;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggerFactory;
import org.apache.log4j.spi.LoggingEvent;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

public class SentryAppenderTest extends AbstractLoggerTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Raven mockRaven;
    @Mock
    private RavenFactory mockRavenFactory;
    private SentryAppender sentryAppender;
    private Logger logger;

    @Before
    public void setUp() throws Exception {
        sentryAppender = new SentryAppender(mockRaven);
        setMockErrorHandlerOnAppender(sentryAppender);

        when(mockRavenFactory.createRavenInstance(any(Dsn.class))).thenReturn(mockRaven);
        RavenFactory.registerFactory(mockRavenFactory);

        logger = Logger.getLogger(SentryAppenderTest.class);
        logger.setLevel(Level.ALL);
        logger.setAdditivity(false);
        logger.addAppender(new SentryAppender(getMockRaven()));
    }

    private void setMockErrorHandlerOnAppender(SentryAppender sentryAppender) {
        ErrorHandler mockErrorHandler = mock(ErrorHandler.class);
        sentryAppender.setErrorHandler(mockErrorHandler);
        Answer<Void> answer = new Answer<Void>() {
            private final ErrorHandler actualErrorHandler = new OnlyOnceErrorHandler();
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                invocation.getMethod().invoke(actualErrorHandler, invocation.getArguments());
                return null;
            }
        };
        doAnswer(answer).when(mockErrorHandler).error(anyString());
        doAnswer(answer).when(mockErrorHandler).error(anyString(), any(Exception.class), anyInt());
        doAnswer(answer).when(mockErrorHandler).error(anyString(), any(Exception.class), anyInt(), any(LoggingEvent.class));
    }

    @Test
    public void testSimpleMessageLogging() throws Exception {
        String message = UUID.randomUUID().toString();
        String loggerName = UUID.randomUUID().toString();
        String threadName = UUID.randomUUID().toString();
        Date date = new Date(1373883196416L);
        Logger logger = Logger.getLogger(loggerName);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        Event event;

        sentryAppender.append(new LoggingEvent(null, logger, date.getTime(), Level.INFO, message, threadName,
                null, null, null, null));

        verify(mockRaven).runBuilderHelpers(any(EventBuilder.class));
        verify(mockRaven).sendEvent(eventCaptor.capture());
        event = eventCaptor.getValue();
        assertThat(event.getMessage(), is(message));
        assertThat(event.getLogger(), is(loggerName));
        assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, threadName));
        assertThat(event.getTimestamp(), is(date));

        assertNoErrors(sentryAppender);
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
    public String getUnformattedMessage() {
        throw new UnsupportedOperationException();
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

    @Test
    public void testThreadNameAddedToExtra() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        logger.info("testMessage");

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(),
                Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, Thread.currentThread().getName()));
    }

    @Test
    public void testMdcAddedToExtra() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        String extraKey = UUID.randomUUID().toString();
        Object extraValue = mock(Object.class);

        MDC.put(extraKey, extraValue);

        logger.info("testMessage");

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(), hasEntry(extraKey, extraValue));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNdcAddedToExtra() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        String extra = UUID.randomUUID().toString();
        String extra2 = UUID.randomUUID().toString();

        NDC.push(extra);
        logger.info("testMessage");

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(), hasKey(SentryAppender.LOG4J_NDC));
        assertThat(eventCaptor.getValue().getExtra().get(SentryAppender.LOG4J_NDC), Matchers.<Object>is(extra));

        reset(mockRaven);
        NDC.push(extra2);
        logger.info("testMessage");

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(), hasKey(SentryAppender.LOG4J_NDC));
        assertThat(eventCaptor.getValue().getExtra().get(SentryAppender.LOG4J_NDC),
                Matchers.<Object>is(extra + " " + extra2));
    }

    private void assertNoErrors(SentryAppender sentryAppender) {
        verify(sentryAppender.getErrorHandler(), never()).error(anyString());
        verify(sentryAppender.getErrorHandler(), never()).error(anyString(), any(Exception.class), anyInt());
        verify(sentryAppender.getErrorHandler(), never()).error(anyString(), any(Exception.class), anyInt(), any(LoggingEvent.class));
    }
}
