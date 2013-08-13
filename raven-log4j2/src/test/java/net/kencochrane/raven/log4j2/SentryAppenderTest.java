package net.kencochrane.raven.log4j2;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.event.Event;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.DefaultErrorHandler;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class SentryAppenderTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Raven mockRaven;
    @Mock
    private RavenFactory mockRavenFactory;
    @Mock
    private DefaultErrorHandler mockErrorHandler;
    private SentryAppender sentryAppender;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
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
