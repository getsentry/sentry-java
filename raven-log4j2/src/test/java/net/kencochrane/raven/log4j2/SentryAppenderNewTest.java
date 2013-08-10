package net.kencochrane.raven.log4j2;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Verifications;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderNewTest {
    private SentryAppender sentryAppender;
    private MockUpErrorHandler mockUpErrorHandler;
    @Injectable
    private Raven mockRaven = null;


    @BeforeMethod
    public void setUp() {
        sentryAppender = new SentryAppender(mockRaven);
        mockUpErrorHandler = new MockUpErrorHandler();
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());
    }

    @Test
    public void testSimpleMessageLogging() throws Exception {
        final String loggerName = UUID.randomUUID().toString();
        final String message = UUID.randomUUID().toString();
        final String threadName = UUID.randomUUID().toString();
        final Date date = new Date(1373883196416L);

        sentryAppender.append(new Log4jLogEvent(loggerName, null, null, Level.INFO, new SimpleMessage(message),
                null, null, null, threadName, null, date.getTime()));

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getMessage(), is(message));
            assertThat(event.getLogger(), is(loggerName));
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, threadName));
            assertThat(event.getTimestamp(), is(date));
        }};
        assertThat(mockUpErrorHandler.getErrorCount(), is(0));
    }

    @Test
    public void testLevelConversion() throws Exception {
        assertLevelConverted(Event.Level.DEBUG, Level.TRACE);
        assertLevelConverted(Event.Level.DEBUG, Level.DEBUG);
        assertLevelConverted(Event.Level.INFO, Level.INFO);
        assertLevelConverted(Event.Level.WARNING, Level.WARN);
        assertLevelConverted(Event.Level.ERROR, Level.ERROR);
        assertLevelConverted(Event.Level.FATAL, Level.FATAL);
    }

    private void assertLevelConverted(final Event.Level expectedLevel, Level level) throws Exception {
        sentryAppender.append(new Log4jLogEvent(null, null, null, level, new SimpleMessage(""), null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getLevel(), is(expectedLevel));
        }};
        assertThat(mockUpErrorHandler.getErrorCount(), is(0));
    }
}
