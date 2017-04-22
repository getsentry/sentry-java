package io.sentry;

import org.testng.annotations.Test;

import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;

public class DefaultSentryClientFactoryTest {
    @Test
    public void checkServiceLoaderProvidesFactory() throws Exception {
        ServiceLoader<SentryClientFactory> sentryFactories = ServiceLoader.load(SentryClientFactory.class);

        assertThat(sentryFactories, contains(instanceOf(DefaultSentryClientFactory.class)));
    }
}
