package com.getsentry.raven.logback;

import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import mockit.*;
import com.getsentry.raven.Raven;
import com.getsentry.raven.RavenFactory;
import com.getsentry.raven.dsn.Dsn;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderCloseTest {
    @Injectable
    private Raven mockRaven = null;
    @Injectable
    private Context mockContext = null;
    @SuppressWarnings("unused")
    @Mocked("ravenInstance")
    private RavenFactory mockRavenFactory = null;
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
        final SentryAppender sentryAppender = new SentryAppender(mockRaven);
        sentryAppender.setContext(mockContext);
        sentryAppender.start();

        sentryAppender.stop();

        new Verifications() {{
            mockRaven.closeConnection();
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testStopIfRavenInstanceNotProvided() throws Exception {
        final String dsnUri = "protocol://public:private@host/1";
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsnUri;
            RavenFactory.ravenInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockRaven;
        }};
        sentryAppender.start();
        sentryAppender.append(null);

        sentryAppender.stop();

        new Verifications() {{
            mockRaven.closeConnection();
        }};
        //One error, because of the null event.
        assertThat(mockContext.getStatusManager().getCount(), is(1));
    }

    @Test
    public void testStopDoNotFailIfInitFailed() throws Exception {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
        new NonStrictExpectations() {{
            RavenFactory.ravenInstance((Dsn) any, anyString);
            result = new UnsupportedOperationException();
        }};
        sentryAppender.start();
        sentryAppender.append(null);

        sentryAppender.stop();

        //Two errors, one because of the exception, one because of the null event.
        assertThat(mockContext.getStatusManager().getCount(), is(2));
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
        final SentryAppender sentryAppender = new SentryAppender(mockRaven);
        sentryAppender.setContext(mockContext);
        sentryAppender.start();

        sentryAppender.stop();
        sentryAppender.stop();

        new Verifications() {{
            mockRaven.closeConnection();
            times = 1;
        }};
        assertNoErrorsInStatusManager();
    }
}
