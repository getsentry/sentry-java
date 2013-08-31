package net.kencochrane.raven.log4j2;

import mockit.*;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.event.Event;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderFailuresTest {
    private SentryAppender sentryAppender;
    private MockUpErrorHandler mockUpErrorHandler;
    @Injectable
    @NonStrict
    private Raven mockRaven = null;
    @Mocked("ravenInstance")
    private RavenFactory mockRavenFactory;

    @BeforeMethod
    public void setUp() throws Exception {
        sentryAppender = new SentryAppender(mockRaven);
        mockUpErrorHandler = new MockUpErrorHandler();
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());
    }

    @Test
    public void testRavenFailureDoesNotPropagate() throws Exception {
        new Expectations() {{
            mockRaven.sendEvent((Event) any);
            result = new UnsupportedOperationException();
        }};

        sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null));

        assertThat(mockUpErrorHandler.getErrorCount(), is(1));
    }

    @Test
    public void testRavenFactoryFailureDoesNotPropagate() throws Exception {
        new Expectations() {{
            RavenFactory.ravenInstance((Dsn) any, anyString);
            result = new UnsupportedOperationException();
        }};
        SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.setDsn("protocol://public:private@host/1");

        sentryAppender.initRaven();

        assertThat(mockUpErrorHandler.getErrorCount(), is(1));
    }

    @Test
    public void testAppendFailIfCurrentThreadSpawnedByRaven() throws Exception {
        try {
            Raven.RAVEN_THREAD.set(true);

            sentryAppender.append(new Log4jLogEvent(null, null, null, Level.INFO, new SimpleMessage(""), null));

            new Verifications() {{
                mockRaven.sendEvent((Event) any);
                times = 0;
            }};
            assertThat(mockUpErrorHandler.getErrorCount(), is(0));
        } finally {
            Raven.RAVEN_THREAD.remove();
        }
    }
}
