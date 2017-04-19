package io.sentry.jul;

import io.sentry.BaseIT;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

public class SentryHandlerIT extends BaseIT {
    /*
     We filter out loggers that start with `io.sentry`, so we deliberately
     use a custom logger name here.
     */
    private static final Logger logger = Logger.getLogger("jul.SentryHandlerIT");
    private static final Logger sentryLogger = Logger.getLogger(SentryHandlerIT.class.getName());

    @Before
    public void setup() {
        stub200ForProject1Store();
    }

    @Test
    public void testErrorLog() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        logger.log(Level.SEVERE, "This is a test");

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(1);
    }

    @Test
    public void testChainedExceptions() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        logger.log(Level.SEVERE, "This is an exception",
                new UnsupportedOperationException("Test", new UnsupportedOperationException()));

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(1);
    }

    @Test
    public void testNoSentryLogging() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        sentryLogger.log(Level.SEVERE, "This is a test");

        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);
    }

}
