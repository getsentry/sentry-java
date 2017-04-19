package io.sentry.log4j2;

import io.sentry.BaseIT;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class SentryAppenderIT extends BaseIT {
    /*
     We filter out loggers that start with `io.sentry`, so we deliberately
     use a custom logger name here.
     */
    private static final Logger logger = LogManager.getLogger("log4j2.SentryAppenderIT");
    private static final Logger sentryLogger = LogManager.getLogger(SentryAppenderIT.class);

    @Before
    public void setup() {
        stub200ForProject1Store();
    }

    @Test
    public void testErrorLog() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        logger.error("This is a test");

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(1);
    }

    @Test
    public void testChainedExceptions() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        logger.error("This is an exception",
                new UnsupportedOperationException("Test", new UnsupportedOperationException()));

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(1);
    }

    @Test
    public void testNoSentryLogging() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        sentryLogger.error("This is a test");

        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);
    }
}
