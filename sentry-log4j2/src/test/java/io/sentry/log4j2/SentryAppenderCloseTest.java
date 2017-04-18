package io.sentry.log4j2;

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
    public void testConnectionClosedWhenAppenderStopped() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockSentry);
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.start();

        sentryAppender.stop();

        new Verifications() {{
            mockSentry.closeConnection();
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testStopIfSentryInstanceNotProvided() throws Exception {
        final String dsnUri = "protocol://public:private@host/1";
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsnUri;
            SentryFactory.sentryInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockSentry;
        }};
        sentryAppender.start();
        sentryAppender.append(null);

        sentryAppender.stop();

        new Verifications() {{
            mockSentry.closeConnection();
        }};
        //One error, because of the null event.
        assertThat(mockUpErrorHandler.getErrorCount(), is(1));
    }

    @Test
    public void testStopDoNotFailIfInitFailed() throws Exception {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());
        new NonStrictExpectations() {{
            SentryFactory.sentryInstance((Dsn) any, anyString);
            result = new UnsupportedOperationException();
        }};
        sentryAppender.start();
        sentryAppender.append(null);

        sentryAppender.stop();

        //Two errors, one because of the exception, one because of the null event.
        assertThat(mockUpErrorHandler.getErrorCount(), is(2));
    }

    @Test
    public void testStopDoNotFailIfNoInit()
            throws Exception {
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());

        sentryAppender.stop();

        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testStopDoNotFailWhenMultipleCalls() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockSentry);
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.start();

        sentryAppender.stop();
        sentryAppender.stop();

        new Verifications() {{
            mockSentry.closeConnection();
            times = 1;
        }};
        assertNoErrorsInErrorHandler();
    }
}
