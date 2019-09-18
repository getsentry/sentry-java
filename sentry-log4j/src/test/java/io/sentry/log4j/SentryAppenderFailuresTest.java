package io.sentry.log4j;

import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.environment.SentryEnvironment;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class SentryAppenderFailuresTest extends BaseTest {
    private SentryAppender sentryAppender;
    private ErrorCounter errorCounter;
    private SentryClient mockSentryClient;
    private Logger fakeLogger = null;

    @Before
    public void setUp() throws Exception {
        mockSentryClient = mock(SentryClient.class);
        Sentry.setStoredClient(mockSentryClient);
        sentryAppender = new SentryAppender();
        errorCounter = new ErrorCounter();
        sentryAppender.setErrorHandler(errorCounter.getErrorHandler());
        sentryAppender.activateOptions();
        fakeLogger = new Logger(null) {};
    }

    @Test
    public void testSentryFailureDoesNotPropagate() throws Exception {
        doThrow(new UnsupportedOperationException()).when(mockSentryClient).sendEvent(any(EventBuilder.class));

        sentryAppender.append(new LoggingEvent(null, fakeLogger, 0, Level.INFO, null, null));

        assertThat(errorCounter.getErrorCount(), is(1));
    }

    @Test
    public void testAppendFailIfCurrentThreadSpawnedBySentry() throws Exception {
        SentryEnvironment.startManagingThread();
        try {
            sentryAppender.append(new LoggingEvent(null, fakeLogger, 0, Level.INFO, null, null));

            verify(mockSentryClient, never()).sendEvent(any(Event.class));
            assertThat(errorCounter.getErrorCount(), is(0));
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }
}
