package net.kencochrane.raven.log4j;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderDsnTest {
    private SentryAppender sentryAppender;
    private MockUpErrorHandler mockUpErrorHandler;
    @Injectable
    private Raven mockRaven = null;
    @Injectable
    private Logger mockLogger = null;
    @Mocked("ravenInstance")
    private RavenFactory mockRavenFactory;
    @Mocked("dsnLookup")
    private Dsn mockDsn;

    @BeforeMethod
    public void setUp() throws Exception {
        sentryAppender = new SentryAppender();
        mockUpErrorHandler = new MockUpErrorHandler();
        sentryAppender.setErrorHandler(mockUpErrorHandler.getMockInstance());
    }

    @AfterMethod
    public void tearDown() throws Exception {
        assertThat(mockUpErrorHandler.getErrorCount(), is(0));
    }

    @Test
    public void testDsnDetected() throws Exception {
        final String dsnUri = "protocol://public:private@host/1";
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsnUri;
            RavenFactory.ravenInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockRaven;
        }};

        sentryAppender.activateOptions();
        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null));
    }

    @Test
    public void testDsnProvided() throws Exception {
        final String dsnUri = "protocol://public:private@host/2";
        sentryAppender.setDsn(dsnUri);
        new Expectations() {{
            RavenFactory.ravenInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockRaven;
        }};

        sentryAppender.activateOptions();
        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null));
    }
}
