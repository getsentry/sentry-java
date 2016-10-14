package com.getsentry.raven.log4j;

import com.getsentry.raven.stub.SentryStub;
import org.apache.log4j.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderIT {
    /*
     We filter out loggers that start with `com.getsentry.raven`, so we deliberately
     use a custom logger name here.
     */
    private static final Logger logger = Logger.getLogger("SentryAppenderIT: log4j");
    private static final Logger ravenLogger = Logger.getLogger(SentryAppenderIT.class);
    private SentryStub sentryStub;

    @BeforeMethod
    public void setUp() throws Exception {
        sentryStub = new SentryStub();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        sentryStub.removeEvents();
    }

    @Test
    public void testInfoLog() throws Exception {
        assertThat(sentryStub.getEventCount(), is(0));
        logger.info("This is a test");
        assertThat(sentryStub.getEventCount(), is(1));
    }

    @Test
    public void testChainedExceptions() throws Exception {
        assertThat(sentryStub.getEventCount(), is(0));
        logger.error("This is an exception",
                new UnsupportedOperationException("Test", new UnsupportedOperationException()));
        assertThat(sentryStub.getEventCount(), is(1));
    }

    @Test
    public void testNoRavenLogging() throws Exception {
        assertThat(sentryStub.getEventCount(), is(0));
        ravenLogger.error("This is a test");
        assertThat(sentryStub.getEventCount(), is(0));
    }
}
