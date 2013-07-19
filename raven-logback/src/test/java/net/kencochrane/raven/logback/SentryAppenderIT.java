package net.kencochrane.raven.logback;

import net.kencochrane.raven.stub.SentryStub;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderIT {
    private static final Logger logger = LoggerFactory.getLogger(SentryAppenderIT.class);
    private SentryStub sentryStub;

    @Before
    public void setUp() {
        sentryStub = new SentryStub();
        sentryStub.removeEvents();
    }

    @After
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
