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

public class SentryAppenderDsnTest {
    @Tested
    private SentryAppender sentryAppender = null;
    @Injectable
    private SentryClient mockSentryClient = null;
    @Injectable
    private Context mockContext = null;
    @SuppressWarnings("unused")
    @Mocked("sentryInstance")
    private SentryClientFactory mockSentryClientFactory = null;
    @SuppressWarnings("unused")
    @Mocked("dsnLookup")
    private Dsn mockDsn = null;

    @BeforeMethod
    public void setUp() throws Exception {
        new MockUpStatusPrinter();
        sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);

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
    public void testDsnDetected() throws Exception {
        final String dsnUri = "protocol://public:private@host/1";
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsnUri;
            SentryClientFactory.sentryInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockSentryClient;
        }};

        sentryAppender.initSentry();

        assertNoErrorsInStatusManager();
    }

    @Test
    public void testDsnProvided() throws Exception {
        final String dsnUri = "protocol://public:private@host/2";
        sentryAppender.setDsn(dsnUri);
        new Expectations() {{
            SentryClientFactory.sentryInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockSentryClient;
        }};

        sentryAppender.initSentry();

        assertNoErrorsInStatusManager();
    }
}
