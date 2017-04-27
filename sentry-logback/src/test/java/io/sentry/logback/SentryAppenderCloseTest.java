package io.sentry.logback;

import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import mockit.*;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import io.sentry.dsn.Dsn;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderCloseTest {
    @Injectable
    private SentryClient mockSentryClient = null;
    @Injectable
    private Context mockContext = null;
    @SuppressWarnings("unused")
    @Mocked("sentryClient")
    private SentryClientFactory mockSentryClientFactory = null;
    @SuppressWarnings("unused")
    @Mocked("dsnLookup")
    private Dsn mockDsn = null;

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

    private void assertNoErrorsInStatusManager() throws Exception {
        assertThat(mockContext.getStatusManager().getCount(), is(0));
    }

    @Test
    public void testConnectionClosedWhenAppenderStopped() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockSentryClient);
        sentryAppender.setContext(mockContext);
        sentryAppender.start();

        sentryAppender.stop();

        new Verifications() {{
            mockSentryClient.closeConnection();
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testStopIfSentryClientNotProvided() throws Exception {
        final String dsnUri = "protocol://public:private@host/1";
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsnUri;
            SentryClientFactory.sentryClient(withEqual(new Dsn(dsnUri)), anyString);
            result = mockSentryClient;
        }};

        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);

        sentryAppender.start();
        sentryAppender.append(null);
        sentryAppender.stop();

        new Verifications() {{
            mockSentryClient.closeConnection();
        }};
        //One error, because of the null event.
        assertThat(mockContext.getStatusManager().getCount(), is(1));
    }

    @Test
    public void testStopDoNotFailIfInitFailed() throws Exception {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        final String dsnUri = "protocol://public:private@host/1";
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsnUri;
            SentryClientFactory.sentryClient(withEqual(new Dsn(dsnUri)), anyString);
            result = new UnsupportedOperationException();
        }};
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);

        sentryAppender.start();
        sentryAppender.append(null);
        sentryAppender.stop();

        assertThat(mockContext.getStatusManager().getCount(), is(1));
    }

    @Test
    public void testStopDoNotFailIfNoInit()
            throws Exception {
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);

        sentryAppender.stop();

        assertNoErrorsInStatusManager();
    }

    @Test
    public void testStopDoNotFailWhenMultipleCalls() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockSentryClient);
        sentryAppender.setContext(mockContext);
        sentryAppender.start();

        sentryAppender.stop();
        sentryAppender.stop();

        new Verifications() {{
            mockSentryClient.closeConnection();
            times = 1;
        }};
        assertNoErrorsInStatusManager();
    }
}
