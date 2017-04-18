package io.sentry.log4j2;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import io.sentry.dsn.Dsn;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderDsnTest {
    @Tested
    private SentryAppender sentryAppender = null;
    private MockUpErrorHandler mockUpErrorHandler = new MockUpErrorHandler();
    @Injectable
    private SentryClient mockSentryClient = null;
    @SuppressWarnings("unused")
    @Mocked("sentryInstance")
    private SentryClientFactory mockSentryClientFactory = null;
    @SuppressWarnings("unused")
    @Mocked("dsnLookup")
    private Dsn mockDsn = null;

    @BeforeMethod
    public void setUp() throws Exception {
        sentryAppender = new SentryAppender();
        sentryAppender.setHandler(mockUpErrorHandler.getMockInstance());
    }

    private void assertNoErrorsInErrorHandler() throws Exception {
        assertThat(mockUpErrorHandler.getErrorCount(), is(0));
    }

    @Test
    public void testDsnDetected() throws Exception {
        final String dsnUri = "protocol://public:private@host/1";
        new Expectations() {{
            Dsn.dsnLookup();
            result = dsnUri;
            SentryClientFactory.sentryInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockSentryClient;
        }};

        sentryAppender.initSentry();

        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testDsnProvided() throws Exception {
        final String dsnUri = "protocol://public:private@host/2";
        sentryAppender.setDsn(dsnUri);
        new Expectations() {{
            SentryClientFactory.sentryInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = mockSentryClient;
        }};

        sentryAppender.initSentry();

        assertNoErrorsInErrorHandler();
    }
}
