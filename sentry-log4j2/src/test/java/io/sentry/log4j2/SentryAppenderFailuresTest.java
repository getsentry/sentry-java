package io.sentry.log4j2;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.EventBuilder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.Before;
import org.junit.Test;

public class SentryAppenderFailuresTest extends BaseTest {
    private SentryAppender sentryAppender;
    private ErrorCounter errorCounter;
    private SentryClient mockSentryClient = null;

    @Before
    public void setUp() throws Exception {
        mockSentryClient = mock(SentryClient.class);
        Sentry.setStoredClient(mockSentryClient);
        sentryAppender = new SentryAppender();
        errorCounter = new ErrorCounter();
        sentryAppender.setHandler(errorCounter.getErrorHandler());
    }

    @Test
    public void testSentryFailureDoesNotPropagate() throws Exception {
        doThrow(new UnsupportedOperationException()).when(mockSentryClient).sendEvent(any(EventBuilder.class));

        sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null));

        assertThat(errorCounter.getErrorCount(), is(1));
    }

    @Test
    public void testAppendFailIfCurrentThreadSpawnedBySentry() throws Exception {
        SentryEnvironment.startManagingThread();
        try {
            sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null));

            verify(mockSentryClient, never()).sendEvent(any(EventBuilder.class));
            assertThat(errorCounter.getErrorCount(), is(0));
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }
}
