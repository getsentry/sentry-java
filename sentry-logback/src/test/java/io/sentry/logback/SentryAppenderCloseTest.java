package io.sentry.logback;

import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SentryAppenderCloseTest extends BaseTest {
    private SentryClient mockSentryClient = null;
    private Context mockContext = null;

    @Before
    public void setUp() throws Exception {
        mockSentryClient = mock(SentryClient.class);

        final BasicStatusManager statusManager = new BasicStatusManager();
        final OnConsoleStatusListener listener = new OnConsoleStatusListener();
        listener.start();
        statusManager.add(listener);

        mockContext = mock(Context.class);
        when(mockContext.getStatusManager()).thenReturn(statusManager);
    }

    private void assertNoErrorsInStatusManager() throws Exception {
        assertThat(mockContext.getStatusManager().getCount(), is(0));
    }

    @Test
    public void testConnectionClosedWhenAppenderStopped() throws Exception {
        Sentry.setStoredClient(mockSentryClient);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
        sentryAppender.start();

        sentryAppender.stop();

        verify(mockSentryClient).closeConnection();
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testStopDoNotFailIfSentryNull() throws Exception {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        Sentry.setStoredClient(null);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);

        sentryAppender.start();
        sentryAppender.stop();

        //Two errors, one because of the exception, one because of the null event.
        assertThat(mockContext.getStatusManager().getCount(), is(0));
    }

    @Test
    public void testStopDoNotFailIfNoInit() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);

        sentryAppender.stop();

        assertNoErrorsInStatusManager();
    }

    @Test
    public void testStopDoNotFailWhenMultipleCalls() throws Exception {
        Sentry.setStoredClient(mockSentryClient);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
        sentryAppender.start();

        sentryAppender.stop();
        sentryAppender.stop();

        verify(mockSentryClient).closeConnection();
        assertNoErrorsInStatusManager();
    }
}
