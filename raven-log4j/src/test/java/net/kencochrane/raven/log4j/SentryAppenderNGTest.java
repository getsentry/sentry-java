package net.kencochrane.raven.log4j;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Verifications;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderNGTest {
    private SentryAppender sentryAppender;
    @Mocked
    private Raven mockRaven = null;
    @Injectable
    private Logger mockLogger = null;

    @BeforeMethod
    public void setUp() {
        sentryAppender = new SentryAppender(mockRaven);
    }

    @Test
    public void testSimpleMesageLogging() throws Exception {
        final String loggerName = UUID.randomUUID().toString();
        final String message = UUID.randomUUID().toString();
        final String threadName = UUID.randomUUID().toString();
        final Date date = new Date(1373883196416L);
        new Expectations() {{
            onInstance(mockLogger).getName();
            result = loggerName;
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, date.getTime(), Level.INFO, message, threadName,
                null, null, null, null));

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers(withAny(new EventBuilder()));
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getMessage(), is(message));
            assertThat(event.getLogger(), is(loggerName));
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, threadName));
            assertThat(event.getTimestamp(), is(date));
        }};
    }
}
