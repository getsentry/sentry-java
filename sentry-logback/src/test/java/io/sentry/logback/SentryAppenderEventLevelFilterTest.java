package io.sentry.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.Context;
import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.event.EventBuilder;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import io.sentry.SentryClient;
import io.sentry.event.Event;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Felipe G Almeida
 */
public class SentryAppenderEventLevelFilterTest extends BaseTest {
    @Tested
    private SentryAppender sentryAppender = null;
    @Injectable
    private SentryClient mockSentryClient = null;
    @Injectable
    private Context mockContext = null;

    @BeforeMethod
    public void setUp() throws Exception {
        new MockUpStatusPrinter();
        Sentry.setStoredClient(mockSentryClient);
        sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
    }

    @DataProvider(name = "levels")
    private Object[][] levelConversions() {
        return new Object[][]{
                {"ALL", 5},
                {"TRACE", 5},
                {"DEBUG", 4},
                {"INFO", 3},
                {"WARN", 2},
                {"ERROR", 1},
                {"error", 1},
                {"xxx", 4},  // invalid level will be coerced to DEBUG
                {null, 5}};
    }

    @Test(dataProvider = "levels")
    public void testLevelFilter(final String minLevel, final Integer expectedEvents) throws Exception {
        sentryAppender.setMinLevel(minLevel);
        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.TRACE, null, null, null).getMockInstance());
        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.DEBUG, null, null, null).getMockInstance());
        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null).getMockInstance());
        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.WARN, null, null, null).getMockInstance());
        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.ERROR, null, null, null).getMockInstance());

        new Verifications() {{
            mockSentryClient.sendEvent((EventBuilder) any);
            minTimes = expectedEvents;
            maxTimes = expectedEvents;
        }};
    }

    @Test
    public void testDefaultLevelFilter() throws Exception {
        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.TRACE, null, null, null).getMockInstance());
        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.DEBUG, null, null, null).getMockInstance());
        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null).getMockInstance());
        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.WARN, null, null, null).getMockInstance());
        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.ERROR, null, null, null).getMockInstance());

        new Verifications() {{
            mockSentryClient.sendEvent((EventBuilder) any);
            minTimes = 5;
            maxTimes = 5;
        }};
    }

}
