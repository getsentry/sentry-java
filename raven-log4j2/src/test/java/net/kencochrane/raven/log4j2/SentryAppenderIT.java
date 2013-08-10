package net.kencochrane.raven.log4j2;

import net.kencochrane.raven.stub.SentryStub;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderIT {
    private static final Logger logger = LogManager.getLogger(SentryAppenderIT.class);
    private SentryStub sentryStub;

    @BeforeMethod
    public void setUp() {
        sentryStub = new SentryStub();
        sentryStub.removeEvents();
    }

    @AfterMethod
    public void tearDown() {
        sentryStub.removeEvents();
    }

    @Test
    public void testInfoLog() {
        assertThat(sentryStub.getEventCount(), is(0));
        logger.info("This is a test");
        assertThat(sentryStub.getEventCount(), is(1));
    }

    @Test
    public void testChainedExceptions() {
        assertThat(sentryStub.getEventCount(), is(0));
        logger.error("This is an exception",
                new UnsupportedOperationException("Test", new UnsupportedOperationException()));
        assertThat(sentryStub.getEventCount(), is(1));
    }
}
