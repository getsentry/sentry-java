package net.kencochrane.raven.jul;

import net.kencochrane.raven.stub.SentryStub;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryHandlerIT {
    private static final Logger logger = Logger.getLogger(SentryHandlerIT.class.getName());
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
        logger.log(Level.SEVERE, "This is an exception",
                new UnsupportedOperationException("Test", new UnsupportedOperationException()));
        assertThat(sentryStub.getEventCount(), is(1));
    }
}
