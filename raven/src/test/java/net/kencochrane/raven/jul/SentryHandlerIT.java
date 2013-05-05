package net.kencochrane.raven.jul;

import net.kencochrane.raven.SentryStub;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryHandlerIT {
    private static final Logger logger = Logger.getLogger(SentryHandlerIT.class.getCanonicalName());
    private SentryStub sentryStub;

    @Before
    public void setUp() {
        sentryStub = new SentryStub();
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
}
