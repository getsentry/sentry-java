package io.sentry.log4j;

import io.sentry.SentryClient;
import io.sentry.event.EventBuilder;
import mockit.*;
import io.sentry.SentryClientFactory;
import io.sentry.dsn.Dsn;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderFailuresTest {
    @Tested
    private SentryAppender sentryAppender = null;
    private MockUpErrorHandler mockUpErrorHandler;
    @Injectable
    private SentryClient mockSentryClient = null;
    @Injectable
    private Logger mockLogger = null;
    @SuppressWarnings("unused")
    @Mocked("sentryClient")
    private SentryClientFactory mockSentryClientFactory = null;

    @BeforeMethod
    public void setUp() throws Exception {
        sentryAppender = new SentryAppender(mockSentryClient);
        mockUpErrorHandler = new MockUpErrorHandler();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();
    }

    @Test
    public void testSentryFailureDoesNotPropagate() throws Exception {
        new NonStrictExpectations() {{
            mockSentryClient.sendEvent((EventBuilder) any);
            result = new UnsupportedOperationException();
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.INFO, null, null));

        assertThat(mockUpErrorHandler.getErrorCount(), is(1));
    }

    @Test
    public void testSentryClientFactoryFailureDoesNotPropagate() throws Exception {
        new NonStrictExpectations() {{
            SentryClientFactory.sentryClient((Dsn) any, anyString);
            result = new UnsupportedOperationException();
        }};
        SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.setDsn("protocol://public:private@host/1");

        sentryAppender.initSentry();

        assertThat(mockUpErrorHandler.getErrorCount(), is(1));
    }

    @Test
    public void testAppendFailIfCurrentThreadSpawnedBySentry() throws Exception {
        SentryEnvironment.startManagingThread();
        try {
            sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.INFO, null, null));

            new Verifications() {{
                mockSentryClient.sendEvent((EventBuilder) any);
                times = 0;
            }};
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }
}
