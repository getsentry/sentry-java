package io.sentry.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.event.EventBuilder;
import mockit.*;
import io.sentry.SentryClientFactory;
import io.sentry.dsn.Dsn;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderFailuresTest extends BaseTest {
    @Injectable
    private SentryClient mockSentryClient = null;
    @Injectable
    private Context mockContext = null;
    @SuppressWarnings("unused")
    @Mocked("sentryClient")
    private SentryClientFactory mockSentryClientFactory;

    @BeforeMethod
    public void setUp() throws Exception {
        new MockUpStatusPrinter();
        new NonStrictExpectations() {{
            final BasicStatusManager statusManager = new BasicStatusManager();
            final OnConsoleStatusListener listener = new OnConsoleStatusListener();
            listener.start();
            statusManager.add(listener);

            mockContext.getStatusManager();
            result = statusManager;
        }};
    }

    @Test
    public void testSentryFailureDoesNotPropagate() throws Exception {
        Sentry.setStoredClient(mockSentryClient);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
        sentryAppender.setMinLevel("ALL");
        new NonStrictExpectations() {{
            mockSentryClient.sendEvent((EventBuilder) any);
            result = new UnsupportedOperationException();
        }};
        sentryAppender.start();

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null).getMockInstance());

        new Verifications() {{
            mockSentryClient.sendEvent((EventBuilder) any);
        }};
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

            sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null).getMockInstance());

            new Verifications() {{
                mockSentryClient.sendEvent((EventBuilder) any);
                times = 0;
            }};
            assertThat(mockContext.getStatusManager().getCount(), is(0));
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }
}
