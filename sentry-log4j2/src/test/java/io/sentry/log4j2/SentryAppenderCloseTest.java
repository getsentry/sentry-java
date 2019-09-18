package io.sentry.log4j2;

import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SentryAppenderCloseTest extends BaseTest {
    private ErrorCounter errorCounter;
    private SentryClient mockSentryClient = null;

    @Before
    public void setUp() throws Exception {
        errorCounter = new ErrorCounter();
        mockSentryClient = mock(SentryClient.class);
    }

    private void assertNoErrorsInErrorHandler() throws Exception {
        assertThat(errorCounter.getErrorCount(), is(0));
    }

    @Test
    public void testConnectionClosedWhenAppenderStopped() throws Exception {
        Sentry.setStoredClient(mockSentryClient);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setHandler(errorCounter.getErrorHandler());
        sentryAppender.start();

        sentryAppender.stop();

        verify(mockSentryClient).closeConnection();
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testStopDoNotFailIfSentryNull() throws Exception {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        Sentry.setStoredClient(null);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setHandler(errorCounter.getErrorHandler());

        sentryAppender.start();
        sentryAppender.stop();

        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testStopDoNotFailIfNoInit()
            throws Exception {
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setHandler(errorCounter.getErrorHandler());

        sentryAppender.stop();

        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testStopDoNotFailWhenMultipleCalls() throws Exception {
        Sentry.setStoredClient(mockSentryClient);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setHandler(errorCounter.getErrorHandler());
        sentryAppender.start();

        sentryAppender.stop();
        sentryAppender.stop();

        verify(mockSentryClient).closeConnection();
        assertNoErrorsInErrorHandler();
    }
}
