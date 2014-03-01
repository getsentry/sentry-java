package net.kencochrane.raven.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import mockit.*;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.event.Event;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderFailuresTest {
    @Injectable
    private Raven mockRaven = null;
    @Injectable
    private Context mockContext = null;
    @Mocked("ravenInstance")
    private RavenFactory mockRavenFactory;

    @BeforeMethod
    public void setUp() throws Exception {
        new MockUpStatusPrinter();
        new NonStrictExpectations() {{
            final BasicStatusManager statusManager = new BasicStatusManager();
            final OnConsoleStatusListener listener = new OnConsoleStatusListener();
            listener.start();
            statusManager.add(listener);

            mockContext.getStatusManager();
            result = statusManager;
        }};
    }

    @Test
    public void testRavenFailureDoesNotPropagate() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockRaven);
        sentryAppender.setContext(mockContext);
        new NonStrictExpectations() {{
            mockRaven.sendEvent((Event) any);
            result = new UnsupportedOperationException();
        }};
        sentryAppender.start();

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null).getMockInstance());

        new Verifications() {{
            mockRaven.sendEvent((Event) any);
        }};
        assertThat(mockContext.getStatusManager().getCount(), is(1));
    }

    @Test
    public void testRavenFactoryFailureDoesNotPropagate() throws Exception {
        final String dsnUri = "proto://private:public@host/1";
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
        sentryAppender.setDsn(dsnUri);
        new Expectations() {{
            RavenFactory.ravenInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = new UnsupportedOperationException();
        }};
        sentryAppender.start();

        sentryAppender.initRaven();

        assertThat(mockContext.getStatusManager().getCount(), is(1));
    }

    @Test
    public void testAppendFailIfCurrentThreadSpawnedByRaven() throws Exception {
        try {
            Raven.startManagingThread();
            final SentryAppender sentryAppender = new SentryAppender(mockRaven);
            sentryAppender.setContext(mockContext);
            sentryAppender.start();

            sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null).getMockInstance());

            new Verifications() {{
                mockRaven.sendEvent((Event) any);
                times = 0;
            }};
            assertThat(mockContext.getStatusManager().getCount(), is(0));
        } finally {
            Raven.stopManagingThread();
        }
    }
}
