package net.kencochrane.raven.log4j;

import org.junit.Test;

import static org.junit.Assert.fail;

public class SentryAppenderBrokenTest {

    @Test
    public void testFailedOpenClose() {
        // This checks that even if sentry wasn't setup correctly it's appender can still be closed.
        SentryAppender appender = new SentryAppender();
        appender.activateOptions();
        try {
            appender.close();
        } catch (Exception e) {
            fail("Even if sentry didn't get configured we shouldn't throw an error when closing.");
        }
    }
}
