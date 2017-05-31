package io.sentry.log4j;

import io.sentry.BaseTest;
import io.sentry.Sentry;
import mockit.*;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import io.sentry.dsn.Dsn;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderCloseTest extends BaseTest {
    private MockUpErrorHandler mockUpErrorHandler;
    @Injectable
    private SentryClient mockSentryClient = null;
    @SuppressWarnings("unused")
    @Mocked("sentryClient")
    private SentryClientFactory mockSentryClientFactory = null;
    @SuppressWarnings("unused")
    @Mocked("dsnLookup")
    private Dsn mockDsn = null;

    @BeforeMethod
    public void setUp() throws Exception {
        mockUpErrorHandler = new MockUpErrorHandler();
    }

    private void assertNoErrorsInErrorHandler() throws Exception {
        assertThat(mockUpErrorHandler.getErrorCount(), is(0));
    }

    @Test
    public void testConnectionClosedWhenAppenderClosed() throws Exception {
        Sentry.setStoredClient(mockSentryClient);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();

        sentryAppender.close();

        new Verifications() {{
            mockSentryClient.closeConnection();
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testCloseDoNotFailIfSentryNull() throws Exception {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        Sentry.setStoredClient(null);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());

        sentryAppender.close();

        assertThat(mockUpErrorHandler.getErrorCount(), is(0));
    }

    @Test
    public void testCloseDoNotFailIfNoInit()
            throws Exception {
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());

        sentryAppender.close();

        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testCloseDoNotFailWhenMultipleCalls() throws Exception {
        Sentry.setStoredClient(mockSentryClient);
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();

        sentryAppender.close();
        sentryAppender.close();

        new Verifications() {{
            mockSentryClient.closeConnection();
            times = 1;
        }};
        assertNoErrorsInErrorHandler();
    }
}
