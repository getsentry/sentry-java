package net.kencochrane.raven.log4j;

import mockit.*;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.dsn.Dsn;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SentryAppenderClosingConnectionTest {
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
    public void setUp() {
        mockUpErrorHandler = new MockUpErrorHandler();
        new NonStrictExpectations() {{
            mockRaven.getConnection();
            result = mockConnection;
        }};
    }

    @Test
    public void testNotClosedIfRavenInstanceIsProvided() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockRaven);
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();

        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
            times = 0;
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        }};
    }

    @Test
    public void testClosedIfRavenInstanceProvidedAndForceClose() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockRaven, true);
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();

        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        }};
    }

    @Test
    public void testNotClosedIfRavenInstanceProvidedAndNotForceClose() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockRaven, false);
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();

        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
            times = 0;
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        }};
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
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        }};
    }

    @Test
    public void testCloseDoNotFailIfInitFailed() throws Exception {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        new NonStrictExpectations() {{
            RavenFactory.ravenInstance((Dsn) any, anyString);
            result = new UnsupportedOperationException();
        }};
        sentryAppender.activateOptions();

        sentryAppender.close();

        new Verifications() {{
            assertThat(mockUpErrorHandler.getErrorCount(), is(1));
        }};
    }

    @Test
    public void testCloseDoNotFailIfNoInit()
            throws Exception {
        SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());

        sentryAppender.close();

        new Verifications() {{
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        }};
    }

    @Test
    public void testCloseDoNotFailWhenMultipleCalls() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockRaven, true);
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();

        sentryAppender.close();
        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
            times = 1;
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        }};
    }
}
