package net.kencochrane.raven.log4j2;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.DefaultErrorHandler;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.DefaultThreadContextStack;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SentryAppenderTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Raven mockRaven;
    @Mock
    private RavenFactory mockRavenFactory;
    @Mock
    private DefaultErrorHandler mockErrorHandler;
    private SentryAppender sentryAppender;

    @Before
    public void setUp() throws Exception {
        sentryAppender = new SentryAppender(mockRaven);
        setMockErrorHandlerOnAppender(sentryAppender);

        when(mockRavenFactory.createRavenInstance(any(Dsn.class))).thenReturn(mockRaven);
        RavenFactory.registerFactory(mockRavenFactory);
    }

    private void setMockErrorHandlerOnAppender(final SentryAppender sentryAppender) {
        sentryAppender.setHandler(mockErrorHandler);

        Answer<Void> answer = new Answer<Void>() {
            private final DefaultErrorHandler actualErrorHandler = new DefaultErrorHandler(sentryAppender);

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                invocation.getMethod().invoke(actualErrorHandler, invocation.getArguments());
                return null;
            }
        };
        doAnswer(answer).when(mockErrorHandler).error(anyString());
        doAnswer(answer).when(mockErrorHandler).error(anyString(), any(Throwable.class));
        doAnswer(answer).when(mockErrorHandler).error(anyString(), any(LogEvent.class), any(Throwable.class));
    }

    @Test
    public void testLogParametrisedMessage() throws Exception {
        String messagePattern = "Formatted message {} {} {}";
        Object[] parameters = {"first parameter", new Object[0], null};
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO,
                new FormattedMessage(messagePattern, parameters), null));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        MessageInterface messageInterface = (MessageInterface) eventCaptor.getValue().getSentryInterfaces()
                .get(MessageInterface.MESSAGE_INTERFACE);

        assertThat(eventCaptor.getValue().getMessage(), is("Formatted message first parameter [] null"));
        assertThat(messageInterface.getMessage(), is(messagePattern));
        assertThat(messageInterface.getParameters(),
                is(Arrays.asList(parameters[0].toString(), parameters[1].toString(), null)));
        assertNoErrors();
    }

    @Test
    public void testMarkerAddedToTag() throws Exception {
        String markerName = UUID.randomUUID().toString();
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(new Log4jLogEvent(null, MarkerManager.getMarker(markerName), null, Level.INFO,
                new SimpleMessage(""), null));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTags(),
                Matchers.<String, Object>hasEntry(SentryAppender.LOG4J_MARKER, markerName));
        assertNoErrors();
    }

    @Test
    public void testMdcAddedToExtra() throws Exception {
        String extraKey = UUID.randomUUID().toString();
        String extraValue = UUID.randomUUID().toString();
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null,
                Collections.singletonMap(extraKey, extraValue), null, null, null, 0));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
        assertNoErrors();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNdcAddedToExtra() throws Exception {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        ThreadContext.ContextStack contextStack = new DefaultThreadContextStack(true);

        contextStack.push(UUID.randomUUID().toString());
        contextStack.push(UUID.randomUUID().toString());
        contextStack.push(UUID.randomUUID().toString());
        sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null, null,
                contextStack, null, null, 0));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat((List<String>) eventCaptor.getValue().getExtra().get(SentryAppender.LOG4J_NDC),
                equalTo(contextStack.asList()));
        assertNoErrors();
    }

    @Test
    public void testSourceUsedAsStacktrace() throws Exception {
        StackTraceElement location = new StackTraceElement(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 42);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null, null, null,
                null, location, 0));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        StackTraceInterface stackTraceInterface = (StackTraceInterface) eventCaptor.getValue().getSentryInterfaces()
                .get(StackTraceInterface.STACKTRACE_INTERFACE);
        assertThat(stackTraceInterface.getStackTrace(), arrayWithSize(1));
        assertThat(stackTraceInterface.getStackTrace()[0], is(location));
        assertNoErrors();
    }

    @Test
    public void testCulpritWithSource() throws Exception {
        StackTraceElement location = new StackTraceElement("a", "b", "c", 42);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null, null, null,
                null, location, 0));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getCulprit(), is("a.b(c:42)"));
        assertNoErrors();
    }

    @Test
    public void testCulpritWithoutSource() throws Exception {
        String loggerName = UUID.randomUUID().toString();
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        sentryAppender.append(new Log4jLogEvent(loggerName, null, null, Level.INFO, new SimpleMessage(""), null));

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getCulprit(), is(loggerName));
        assertNoErrors();
    }

    @Test
    public void testClose() throws Exception {
        sentryAppender.stop();
        verify(mockRaven.getConnection(), never()).close();

        sentryAppender = new SentryAppender(mockRaven, true);
        setMockErrorHandlerOnAppender(sentryAppender);

        sentryAppender.stop();
        verify(mockRaven.getConnection()).close();
        assertNoErrors();
    }

    @Test
    public void testAppendFailIfCurrentThreadSpawnedByRaven() throws Exception {
        try {
            Raven.RAVEN_THREAD.set(true);

            sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null));

            verify(mockRaven, never()).sendEvent(any(Event.class));
            assertNoErrors();
        } finally {
            Raven.RAVEN_THREAD.remove();
        }
    }

    @Test
    public void testRavenFailureDoesNotPropagate() throws Exception {
        doThrow(new UnsupportedOperationException()).when(mockRaven).sendEvent(any(Event.class));

        sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null));

        verify(mockErrorHandler, never()).error(anyString());
        verify(mockErrorHandler, never()).error(anyString(), any(Throwable.class));
        verify(mockErrorHandler).error(anyString(), any(LogEvent.class), any(Throwable.class));
    }

    @Test
    public void testLazyInitialisation() throws Exception {
        String dsnUri = "proto://private:public@host/1";
        sentryAppender = new SentryAppender();
        setMockErrorHandlerOnAppender(sentryAppender);
        sentryAppender.setDsn(dsnUri);
        sentryAppender.setRavenFactory(mockRavenFactory.getClass().getName());

        sentryAppender.start();
        verify(mockRavenFactory, never()).createRavenInstance(any(Dsn.class));

        sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null));
        verify(mockRavenFactory).createRavenInstance(eq(new Dsn(dsnUri)));
        assertNoErrors();
    }

    @Test
    public void testDsnAutoDetection() throws Exception {
        try {
            String dsnUri = "proto://private:public@host/1";
            System.setProperty(Dsn.DSN_VARIABLE, dsnUri);
            sentryAppender = new SentryAppender();
            setMockErrorHandlerOnAppender(sentryAppender);
            sentryAppender.setRavenFactory(mockRavenFactory.getClass().getName());

            sentryAppender.start();
            sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null));

            verify(mockRavenFactory).createRavenInstance(eq(new Dsn(dsnUri)));
            assertNoErrors();
        } finally {
            System.clearProperty(Dsn.DSN_VARIABLE);
        }
    }

    private void assertNoErrors() {
        verify(mockErrorHandler, never()).error(anyString());
        verify(mockErrorHandler, never()).error(anyString(), any(Throwable.class));
        verify(mockErrorHandler, never()).error(anyString(), any(LogEvent.class), any(Throwable.class));
    }
}
