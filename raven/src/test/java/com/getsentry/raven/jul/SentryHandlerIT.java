package com.getsentry.raven.jul;

import com.getsentry.raven.stub.SentryStub;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryHandlerIT {
    /*
     We filter out loggers that start with `com.getsentry.raven`, so we deliberately
     use a custom logger name here.
     */
    private static final Logger logger = Logger.getLogger("SentryHandlerIT: jul");
    private static final Logger ravenLogger = Logger.getLogger(SentryHandlerIT.class.getName());
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
        logger.log(Level.SEVERE, "This is an exception",
                new UnsupportedOperationException("Test", new UnsupportedOperationException()));
        assertThat(sentryStub.getEventCount(), is(1));
    }

    @Test
    public void testNoRavenLogging() throws Exception {
        assertThat(sentryStub.getEventCount(), is(0));
        ravenLogger.log(Level.SEVERE, "This is a test");
        assertThat(sentryStub.getEventCount(), is(0));
    }
}
