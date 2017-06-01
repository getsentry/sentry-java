package io.sentry;

import io.sentry.dsn.Dsn;

public class TestFactory extends DefaultSentryClientFactory {
    @Override
    protected SentryClient configureSentryClient(SentryClient sentryClient, Dsn dsn) {
        sentryClient.setRelease("312407214120");
        return super.configureSentryClient(sentryClient, dsn);
    }
}
