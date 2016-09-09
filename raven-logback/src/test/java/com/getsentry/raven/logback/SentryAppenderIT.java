package com.getsentry.raven.logback;

import com.getsentry.raven.stub.SentryStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderIT {
    private static final Logger logger = LoggerFactory.getLogger(SentryAppenderIT.class);
    private SentryStub sentryStub;

    @BeforeMethod
    public void setUp() throws Exception {
        sentryStub = new SentryStub();
        sentryStub.removeEvents();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        sentryStub.removeEvents();
    }

    @Test
    public void testWarnLog() throws Exception {
        assertThat(sentryStub.getEventCount(), is(0));
        logger.warn("This is a test");
        assertThat(sentryStub.getEventCount(), is(1));
    }

    @Test
    public void testChainedExceptions() throws Exception {
        assertThat(sentryStub.getEventCount(), is(0));
        logger.error("This is an exception",
                new UnsupportedOperationException("Test", new UnsupportedOperationException()));
        assertThat(sentryStub.getEventCount(), is(1));
    }
}
