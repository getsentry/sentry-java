package net.kencochrane.raven.log4j;

import mockit.*;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.dsn.Dsn;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SentryAppenderClosingConnectionTest {
    @Mocked
    private Raven mockRaven = null;
    @Mocked
    private Connection mockConnection = null;
    @Injectable
    private ErrorHandler mockErrorHandler = null;

    @BeforeMethod
    public void setUp() {
        new NonStrictExpectations() {{
            mockRaven.getConnection();
            result = mockConnection;
        }};
    }

    @Test
    public void testNotClosedIfRavenInstanceIsProvided() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockRaven);
        sentryAppender.setErrorHandler(mockErrorHandler);

        sentryAppender.activateOptions();
        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
            times = 0;
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testClosedIfRavenInstanceProvidedAndForceClose() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockRaven, true);
        sentryAppender.setErrorHandler(mockErrorHandler);

        sentryAppender.activateOptions();
        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testNotClosedIfRavenInstanceProvidedAndNotForceClose()
            throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockRaven, false);
        sentryAppender.setErrorHandler(mockErrorHandler);

        sentryAppender.activateOptions();
        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
            times = 0;
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testClosedIfRavenInstanceNotProvided() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockErrorHandler);
        new Expectations() {
            @Mocked
            private final Dsn dsn = null;
            @Mocked("ravenInstance")
            private RavenFactory ravenFactory;

            {
                RavenFactory.ravenInstance(withAny(dsn), anyString);
                result = mockRaven;
            }
        };

        sentryAppender.activateOptions();
        sentryAppender.close();

        new Verifications() {{
            mockConnection.close();
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testCloseDoNotFailIfInitFailed() throws Exception {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockErrorHandler);

        sentryAppender.activateOptions();
        sentryAppender.close();

        new Verifications() {{
            mockErrorHandler.error(anyString);
            times = 0;
            mockErrorHandler.error(anyString, (Exception) any, anyInt);
            times = 1;
            mockErrorHandler.error(anyString, (Exception) any, anyInt, (LoggingEvent) any);
            times = 0;
        }};
    }

    @Test
    public void testCloseDoNotFailWhenMultipleCalls() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockRaven);
        sentryAppender.setErrorHandler(mockErrorHandler);

        sentryAppender.close();
        sentryAppender.close();

        new Verifications() {{
            onInstance(mockConnection).close();
            times = 0;
        }};
        assertDoNotGenerateErrors();
    }

    private void assertDoNotGenerateErrors() throws Exception {
        new Verifications() {{
            mockErrorHandler.error(anyString);
            times = 0;
            mockErrorHandler.error(anyString, (Exception) any, anyInt);
            times = 0;
            mockErrorHandler.error(anyString, (Exception) any, anyInt, (LoggingEvent) any);
            times = 0;
        }};
    }
}
