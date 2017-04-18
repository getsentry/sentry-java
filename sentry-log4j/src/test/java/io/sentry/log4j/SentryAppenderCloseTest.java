package io.sentry.log4j;

import mockit.*;
import io.sentry.Sentry;
import io.sentry.SentryFactory;
import io.sentry.dsn.Dsn;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderCloseTest {
    private MockUpErrorHandler mockUpErrorHandler;
    @Injectable
    private Sentry mockSentry = null;
    @SuppressWarnings("unused")
    @Mocked("sentryInstance")
    private SentryFactory mockSentryFactory = null;
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
        final SentryAppender sentryAppender = new SentryAppender(mockSentry);
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();

        sentryAppender.close();

        new Verifications() {{
            mockSentry.closeConnection();
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testClosedIfSentryInstanceNotProvided() throws Exception {
        final String dsnUri = "protocol://public:private@host/1";
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsnUri;
            SentryFactory.sentryInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockSentry;
        }};
        sentryAppender.activateOptions();

        sentryAppender.close();

        new Verifications() {{
            mockSentry.closeConnection();
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testCloseDoNotFailIfInitFailed() throws Exception {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        new NonStrictExpectations() {{
            SentryFactory.sentryInstance((Dsn) any, anyString);
            result = new UnsupportedOperationException();
        }};
        sentryAppender.activateOptions();

        sentryAppender.close();

        assertThat(mockUpErrorHandler.getErrorCount(), is(1));
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
        final SentryAppender sentryAppender = new SentryAppender(mockSentry);
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();

        sentryAppender.close();
        sentryAppender.close();

        new Verifications() {{
            mockSentry.closeConnection();
            times = 1;
        }};
        assertNoErrorsInErrorHandler();
    }
}
