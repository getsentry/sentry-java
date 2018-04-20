package io.sentry.logback;

import io.sentry.BaseIT;
import io.sentry.unmarshaller.event.UnmarshalledEvent;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class SentryAppenderIT extends BaseIT {
    /*
     We filter out loggers that start with `io.sentry`, so we deliberately
     use a custom logger name here.
     */
    private static final Logger logger = LoggerFactory.getLogger("logback.SentryAppenderIT");
    private static final Logger sentryLogger = LoggerFactory.getLogger(SentryAppenderIT.class);

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
    public void testInfoThenErrorLog() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        logger.info("Innocuous event");
        logger.info("Another innocuous event");
        logger.error("This is a test");

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(1);
        UnmarshalledEvent event = getStoredEvents().get(0);
        assertEquals(2, event.getBreadcrumbLength());
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
