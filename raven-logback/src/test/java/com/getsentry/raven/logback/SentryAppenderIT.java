package com.getsentry.raven.logback;

import com.getsentry.raven.BaseIT;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class SentryAppenderIT extends BaseIT {
    /*
     We filter out loggers that start with `com.getsentry.raven`, so we deliberately
     use a custom logger name here.
     */
    private static final Logger logger = LoggerFactory.getLogger("SentryAppenderIT: logback");
    private static final Logger ravenLogger = LoggerFactory.getLogger(SentryAppenderIT.class);

    @Before
    public void setup() {
        wireMockRule.stubFor(post(urlEqualTo(PROJECT1_STORE_URL)).willReturn(aResponse().withStatus(200)));
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
    public void testNoRavenLogging() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        ravenLogger.error("This is a test");

        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);
    }
}
