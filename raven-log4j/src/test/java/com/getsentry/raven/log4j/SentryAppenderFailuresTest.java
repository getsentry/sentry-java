package com.getsentry.raven.log4j;

import mockit.*;
import com.getsentry.raven.Raven;
import com.getsentry.raven.RavenFactory;
import com.getsentry.raven.dsn.Dsn;
import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.Event;
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
    private Raven mockRaven = null;
    @Injectable
    private Logger mockLogger = null;
    @SuppressWarnings("unused")
    @Mocked("ravenInstance")
    private RavenFactory mockRavenFactory = null;

    @BeforeMethod
    public void setUp() throws Exception {
        sentryAppender = new SentryAppender(mockRaven);
        mockUpErrorHandler = new MockUpErrorHandler();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.activateOptions();
    }

    @Test
    public void testRavenFailureDoesNotPropagate() throws Exception {
        new NonStrictExpectations() {{
            mockRaven.sendEvent((Event) any);
            result = new UnsupportedOperationException();
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.INFO, null, null));

        assertThat(mockUpErrorHandler.getErrorCount(), is(1));
    }

    @Test
    public void testRavenFactoryFailureDoesNotPropagate() throws Exception {
        new NonStrictExpectations() {{
            RavenFactory.ravenInstance((Dsn) any, anyString);
            result = new UnsupportedOperationException();
        }};
        SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.setDsn("protocol://public:private@host/1");

        sentryAppender.initRaven();

        assertThat(mockUpErrorHandler.getErrorCount(), is(1));
    }

    @Test
    public void testAppendFailIfCurrentThreadSpawnedByRaven() throws Exception {
        RavenEnvironment.startManagingThread();
        try {
            sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.INFO, null, null));

            new Verifications() {{
                mockRaven.sendEvent((Event) any);
                times = 0;
            }};
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        } finally {
            RavenEnvironment.stopManagingThread();
        }
    }
}
