package net.kencochrane.raven.log4j;

import org.junit.Test;

public class SentryAppenderBrokenTest {

    @Test
    public void testFailedOpenClose() {
        // This checks that even if sentry wasn't setup correctly its appender can still be closed.
        SentryAppender appender = new SentryAppender();
        appender.activateOptions();
        appender.close();
    }
}
