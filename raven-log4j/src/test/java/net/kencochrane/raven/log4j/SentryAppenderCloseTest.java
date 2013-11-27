package net.kencochrane.raven.log4j;

import mockit.*;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.dsn.Dsn;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderCloseTest {
    private MockUpErrorHandler mockUpErrorHandler;
    @Injectable
    private Raven mockRaven = null;
    @Injectable
    private Connection mockConnection = null;
    @Mocked("ravenInstance")
    private RavenFactory mockRavenFactory;
    @Mocked("dsnLookup")
    private Dsn mockDsn;

    @BeforeMethod
    public void setUp() throws Exception {
        mockUpErrorHandler = new MockUpErrorHandler();
        new NonStrictExpectations() {{
            mockRaven.getConnection();
            result = mockConnection;
        }};
    }

    private void assertNoErrorsInErrorHandler() throws Exception {
        assertThat(mockUpErrorHandler.getErrorCount(), is(0));
    }

    @Test
    public void testConnectionClosedWhenAppenderClosed() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockRaven);
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();

        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testClosedIfRavenInstanceNotProvided() throws Exception {
        final String dsnUri = "protocol://public:private@host/1";
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsnUri;
            RavenFactory.ravenInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockRaven;
        }};
        sentryAppender.activateOptions();

        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
        }};
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testCloseDoNotFailIfInitFailed() throws Exception {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        new NonStrictExpectations() {{
            RavenFactory.ravenInstance((Dsn) any, anyString);
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
        final SentryAppender sentryAppender = new SentryAppender(mockRaven);
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();

        sentryAppender.close();
        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
            times = 1;
        }};
        assertNoErrorsInErrorHandler();
    }
}
