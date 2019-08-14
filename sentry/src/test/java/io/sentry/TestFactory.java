package io.sentry;

import io.sentry.dsn.Dsn;

public class TestFactory extends DefaultSentryClientFactory {
    static final String RELEASE = "312407214120";
    @Override
    protected SentryClient configureSentryClient(SentryClient sentryClient, Dsn dsn) {
        sentryClient.setRelease(RELEASE);
        return super.configureSentryClient(sentryClient, dsn);
    }
}
