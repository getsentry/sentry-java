package io.sentry.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.event.EventBuilder;
import io.sentry.environment.SentryEnvironment;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SentryAppenderFailuresTest extends BaseTest {
    private SentryClient mockSentryClient = null;
    private Context mockContext = null;

    @Before
    public void setUp() throws Exception {
        mockSentryClient = mock(SentryClient.class);
        mockContext = mock(Context.class);

        final BasicStatusManager statusManager = new BasicStatusManager();
        final OnConsoleStatusListener listener = new OnConsoleStatusListener();
        listener.start();
        statusManager.add(listener);

        when(mockContext.getStatusManager()).thenReturn(statusManager);
    }

    @Test
    public void testSentryFailureDoesNotPropagate() throws Exception {
        Sentry.setStoredClient(mockSentryClient);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
        sentryAppender.setMinLevel("ALL");

        doThrow(new UnsupportedOperationException()).when(mockSentryClient).sendEvent(any(EventBuilder.class));

        sentryAppender.start();

        sentryAppender.append(new TestLoggingEvent(null, null, Level.INFO, null, null, null));

        verify(mockSentryClient).sendEvent(any(EventBuilder.class));
        assertThat(mockContext.getStatusManager().getCount(), is(1));
    }

    @Test
    public void testAppendFailIfCurrentThreadSpawnedBySentry() throws Exception {
        SentryEnvironment.startManagingThread();
        try {
            Sentry.setStoredClient(mockSentryClient);
            final SentryAppender sentryAppender = new SentryAppender();
            sentryAppender.setContext(mockContext);
            sentryAppender.start();

            sentryAppender.append(new TestLoggingEvent(null, null, Level.INFO, null, null, null));

            verify(mockSentryClient, never()).sendEvent(any(EventBuilder.class));
            assertThat(mockContext.getStatusManager().getCount(), is(0));
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }
}
