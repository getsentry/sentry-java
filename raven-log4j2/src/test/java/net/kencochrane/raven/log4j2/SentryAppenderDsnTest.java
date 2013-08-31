package net.kencochrane.raven.log4j2;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderDsnTest {
    private MockUpErrorHandler mockUpErrorHandler = new MockUpErrorHandler();
    @Injectable
    private Raven mockRaven = null;
    @Mocked("ravenInstance")
    private RavenFactory mockRavenFactory;
    @Mocked("dsnLookup")
    private Dsn dsn;

    private void assertNoErrorsInErrorHandler() throws Exception {
        assertThat(mockUpErrorHandler.getErrorCount(), is(0));
    }

    @Test
    public void testLazyInitialisation() throws Exception {
        final String dsnUri = "proto://private:public@host/1";
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());
        sentryAppender.setDsn(dsnUri);
        new Expectations() {{
            RavenFactory.ravenInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockRaven;
        }};

        sentryAppender.initRaven();

        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testDsnAutoDetection() throws Exception {
        final String dsnUri = "proto://private:public@host/1";
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsnUri;
            RavenFactory.ravenInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockRaven;
        }};

        sentryAppender.initRaven();

        assertNoErrorsInErrorHandler();
    }
}
