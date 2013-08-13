package net.kencochrane.raven.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.hamcrest.Matchers;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class SentryAppenderTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Raven mockRaven;
    @Mock
    private RavenFactory mockRavenFactory;
    private SentryAppender sentryAppender;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        sentryAppender = new SentryAppender(mockRaven);
        setMockContextOnAppender(sentryAppender);

        when(mockRavenFactory.createRavenInstance(any(Dsn.class))).thenReturn(mockRaven);
        RavenFactory.registerFactory(mockRavenFactory);
    }

    private void setMockContextOnAppender(SentryAppender sentryAppender) {
        Context mockContext = mock(Context.class);
        sentryAppender.setContext(mockContext);
        BasicStatusManager statusManager = new BasicStatusManager();
        OnConsoleStatusListener listener = new OnConsoleStatusListener();
        listener.start();
        statusManager.add(listener);
        when(mockContext.getStatusManager()).thenReturn(statusManager);
    }

    @Test
    public void testSimpleMessageLogging() throws Exception {
        String message = UUID.randomUUID().toString();
        String loggerName = UUID.randomUUID().toString();
        String threadName = UUID.randomUUID().toString();
        Date date = new Date(1373883196416L);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        Event event;

        ILoggingEvent loggingEvent = newLoggingEvent(loggerName, null, Level.INFO, message, null, null, null,
                threadName, null, date.getTime());
        sentryAppender.append(loggingEvent);

        verify(mockRaven).runBuilderHelpers(any(EventBuilder.class));
        verify(mockRaven).sendEvent(eventCaptor.capture());
        event = eventCaptor.getValue();
        assertThat(event.getMessage(), is(message));
        assertThat(event.getLogger(), is(loggerName));
        assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, threadName));
        assertThat(event.getTimestamp(), is(date));

        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
    }

    @Test
    public void testLogLevelConversions() throws Exception {
        assertLevelConverted(Event.Level.DEBUG, Level.TRACE);
        assertLevelConverted(Event.Level.DEBUG, Level.DEBUG);
        assertLevelConverted(Event.Level.INFO, Level.INFO);
        assertLevelConverted(Event.Level.WARNING, Level.WARN);
        assertLevelConverted(Event.Level.ERROR, Level.ERROR);
    }

    private void assertLevelConverted(Event.Level expectedLevel, Level level) {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(newLoggingEvent(null, null, level, null, null, null));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getLevel(), is(expectedLevel));
        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
        reset(mockRaven);
    }

    @Test
    public void testExceptionLogging() throws Exception {
        Exception exception = new Exception();
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(newLoggingEvent(null, null, Level.ERROR, null, null, exception));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        ExceptionInterface exceptionInterface = (ExceptionInterface) eventCaptor.getValue().getSentryInterfaces()
                .get(ExceptionInterface.EXCEPTION_INTERFACE);
        Throwable capturedException = exceptionInterface.getThrowable();

        assertThat(capturedException.getMessage(), is(exception.getMessage()));
        assertThat(capturedException.getStackTrace(), is(exception.getStackTrace()));
        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
    }

    @Test
    public void testLogParametrisedMessage() throws Exception {
        String messagePattern = "Formatted message {} {} {}";
        Object[] parameters = {"first parameter", new Object[0], null};
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(newLoggingEvent(null, null, Level.INFO, messagePattern, parameters, null));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        MessageInterface messageInterface = (MessageInterface) eventCaptor.getValue().getSentryInterfaces()
                .get(MessageInterface.MESSAGE_INTERFACE);

        assertThat(eventCaptor.getValue().getMessage(), is("Formatted message first parameter [] null"));
        assertThat(messageInterface.getMessage(), is(messagePattern));
        assertThat(messageInterface.getParameters(),
                is(Arrays.asList(parameters[0].toString(), parameters[1].toString(), null)));
        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
    }

    @Test
    public void testMarkerAddedToTag() throws Exception {
        String markerName = UUID.randomUUID().toString();
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(newLoggingEvent(null, MarkerFactory.getMarker(markerName), Level.INFO, null, null, null));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTags(),
                Matchers.<String, Object>hasEntry(SentryAppender.LOGBACK_MARKER, markerName));
        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
    }

    @Test
    public void testMdcAddedToExtra() throws Exception {
        String extraKey = UUID.randomUUID().toString();
        String extraValue = UUID.randomUUID().toString();
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(newLoggingEvent(null, null, Level.INFO, null, null, null,
                Collections.singletonMap(extraKey, extraValue), null, null, 0));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
    }

    @Test
    public void testSourceUsedAsStacktrace() throws Exception {
        StackTraceElement[] location = {new StackTraceElement(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 42)};
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(newLoggingEvent(null, null, Level.INFO, null, null, null, null, null, location, 0));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        StackTraceInterface stackTraceInterface = (StackTraceInterface) eventCaptor.getValue().getSentryInterfaces()
                .get(StackTraceInterface.STACKTRACE_INTERFACE);
        assertThat(stackTraceInterface.getStackTrace(), is(location));
        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
    }

    @Test
    public void testCulpritWithSource() throws Exception {
        StackTraceElement[] location = {new StackTraceElement("a", "b", "c", 42),
                new StackTraceElement("d", "e", "f", 69)};
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(newLoggingEvent(null, null, Level.INFO, null, null, null, null, null, location, 0));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getCulprit(), is("a.b(c:42)"));
        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
    }

    @Test
    public void testCulpritWithoutSource() throws Exception {
        String loggerName = UUID.randomUUID().toString();
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(newLoggingEvent(loggerName, null, Level.INFO, null, null, null));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getCulprit(), is(loggerName));
        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
    }

    @Test
    public void testClose() throws Exception {
        sentryAppender.stop();
        verify(mockRaven.getConnection(), never()).close();

        sentryAppender = new SentryAppender(mockRaven, true);
        setMockContextOnAppender(sentryAppender);

        sentryAppender.stop();
        verify(mockRaven.getConnection()).close();
        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
    }

    @Test
    public void testAppendFailIfCurrentThreadSpawnedByRaven() throws Exception {
        try {
            Raven.RAVEN_THREAD.set(true);

            sentryAppender.append(newLoggingEvent(null, null, Level.INFO, null, null, null));

            verify(mockRaven, never()).sendEvent(any(Event.class));
            assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
        } finally {
            Raven.RAVEN_THREAD.remove();
        }
    }

    @Test
    public void testRavenFailureDoesNotPropagate() throws Exception {
        doThrow(new UnsupportedOperationException()).when(mockRaven).sendEvent(any(Event.class));

        sentryAppender.append(newLoggingEvent(null, null, Level.INFO, null, null, null));

        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(1));
    }

    @Test
    public void testLazyInitialisation() throws Exception {
        String dsnUri = "proto://private:public@host/1";
        sentryAppender = new SentryAppender();
        setMockContextOnAppender(sentryAppender);
        sentryAppender.setDsn(dsnUri);
        sentryAppender.setRavenFactory(mockRavenFactory.getClass().getName());

        sentryAppender.start();
        verify(mockRavenFactory, never()).createRavenInstance(any(Dsn.class));

        sentryAppender.append(newLoggingEvent(null, null, Level.INFO, null, null, null));
        verify(mockRavenFactory).createRavenInstance(eq(new Dsn(dsnUri)));
        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
    }

    @Test
    public void testDsnAutoDetection() throws Exception {
        try {
            String dsnUri = "proto://private:public@host/1";
            System.setProperty(Dsn.DSN_VARIABLE, dsnUri);
            sentryAppender = new SentryAppender();
            setMockContextOnAppender(sentryAppender);
            sentryAppender.setRavenFactory(mockRavenFactory.getClass().getName());

            sentryAppender.start();
            sentryAppender.append(newLoggingEvent(null, null, Level.INFO, null, null, null));

            verify(mockRavenFactory).createRavenInstance(eq(new Dsn(dsnUri)));
            assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(0));
        } finally {
            System.clearProperty(Dsn.DSN_VARIABLE);
        }
    }

    @Test
    public void testFailedInitialisation() throws Exception {
        String dsnUri = "proto://private:public@host/1";
        sentryAppender = new SentryAppender();
        setMockContextOnAppender(sentryAppender);
        sentryAppender.setDsn(dsnUri);
        sentryAppender.setRavenFactory("invalid factory");

        sentryAppender.append(newLoggingEvent(null, null, Level.INFO, null, null, null));

        assertThat(sentryAppender.getContext().getStatusManager().getCount(), is(1));
    }

    private ILoggingEvent newLoggingEvent(String loggerName, Marker marker, Level level, String message,
                                          Object[] argumentArray, Throwable t) {
        return newLoggingEvent(loggerName, marker, level, message, argumentArray, t,
                null, null, null, System.currentTimeMillis());
    }

    private ILoggingEvent newLoggingEvent(String loggerName, Marker marker, Level level,
                                          String message, Object[] argumentArray, Throwable throwable,
                                          Map<String, String> mdcPropertyMap, String threadName,
                                          StackTraceElement[] callerData, long timestamp) {
        ILoggingEvent iLoggingEvent = mock(ILoggingEvent.class);
        when(iLoggingEvent.getThreadName()).thenReturn(threadName);
        when(iLoggingEvent.getLevel()).thenReturn(level);
        when(iLoggingEvent.getMessage()).thenReturn(message);
        when(iLoggingEvent.getArgumentArray()).thenReturn(argumentArray);
        when(iLoggingEvent.getFormattedMessage()).thenReturn(
                argumentArray != null ? MessageFormatter.arrayFormat(message, argumentArray).getMessage() : message);
        when(iLoggingEvent.getLoggerName()).thenReturn(loggerName);
        when(iLoggingEvent.getThrowableProxy()).thenReturn(throwable != null ? new ThrowableProxy(throwable) : null);
        when(iLoggingEvent.getCallerData()).thenReturn(callerData != null ? callerData : new StackTraceElement[0]);
        when(iLoggingEvent.hasCallerData()).thenReturn(callerData != null && callerData.length > 0);
        when(iLoggingEvent.getMarker()).thenReturn(marker);
        when(iLoggingEvent.getMDCPropertyMap())
                .thenReturn(mdcPropertyMap != null ? mdcPropertyMap : Collections.<String, String>emptyMap());
        when(iLoggingEvent.getTimeStamp()).thenReturn(timestamp);

        return iLoggingEvent;
    }
}
