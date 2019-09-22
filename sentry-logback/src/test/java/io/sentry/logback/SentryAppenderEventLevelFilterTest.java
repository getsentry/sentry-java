package io.sentry.logback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.Context;
import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.event.EventBuilder;
import junitparams.JUnitParamsRunner;
import junitparams.NamedParameters;
import junitparams.Parameters;
import io.sentry.SentryClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Felipe G Almeida
 */
@RunWith(JUnitParamsRunner.class)
public class SentryAppenderEventLevelFilterTest extends BaseTest {
    private SentryAppender sentryAppender = null;
    private SentryClient mockSentryClient = null;
    private Context mockContext = null;

    @Before
    public void setUp() throws Exception {
        mockSentryClient = mock(SentryClient.class);
        mockContext = mock(Context.class);
        
        Sentry.setStoredClient(mockSentryClient);
        sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
    }

    @NamedParameters("levels")
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

    @Test
    @Parameters(named = "levels")
    public void testLevelFilter(final String minLevel, final Integer expectedEvents) throws Exception {
        sentryAppender.setMinLevel(minLevel);
        sentryAppender.append(new TestLoggingEvent(null, null, Level.TRACE, null, null, null));
        sentryAppender.append(new TestLoggingEvent(null, null, Level.DEBUG, null, null, null));
        sentryAppender.append(new TestLoggingEvent(null, null, Level.INFO, null, null, null));
        sentryAppender.append(new TestLoggingEvent(null, null, Level.WARN, null, null, null));
        sentryAppender.append(new TestLoggingEvent(null, null, Level.ERROR, null, null, null));

        verify(mockSentryClient, times(expectedEvents)).sendEvent(any(EventBuilder.class));
    }

    @Test
    public void testDefaultLevelFilter() throws Exception {
        sentryAppender.append(new TestLoggingEvent(null, null, Level.TRACE, null, null, null));
        sentryAppender.append(new TestLoggingEvent(null, null, Level.DEBUG, null, null, null));
        sentryAppender.append(new TestLoggingEvent(null, null, Level.INFO, null, null, null));
        sentryAppender.append(new TestLoggingEvent(null, null, Level.WARN, null, null, null));
        sentryAppender.append(new TestLoggingEvent(null, null, Level.ERROR, null, null, null));

        verify(mockSentryClient, times(5)).sendEvent(any(EventBuilder.class));
    }
}
