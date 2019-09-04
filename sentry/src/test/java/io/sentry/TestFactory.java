package io.sentry;

import io.sentry.config.Lookup;
import io.sentry.dsn.Dsn;

public class TestFactory extends DefaultSentryClientFactory {
    static final String RELEASE = "312407214120";

    public TestFactory(Lookup lookup) {
        super(lookup);
    }

    @Override
    protected SentryClient configureSentryClient(SentryClient sentryClient, Dsn dsn) {
        sentryClient.setRelease(RELEASE);
        return super.configureSentryClient(sentryClient, dsn);
    }
}
