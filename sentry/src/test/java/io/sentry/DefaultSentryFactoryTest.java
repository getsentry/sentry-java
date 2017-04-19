package io.sentry;

import org.testng.annotations.Test;

import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;

public class DefaultSentryFactoryTest {
    @Test
    public void checkServiceLoaderProvidesFactory() throws Exception {
        ServiceLoader<SentryFactory> sentryFactories = ServiceLoader.load(SentryFactory.class);

        assertThat(sentryFactories, contains(instanceOf(DefaultSentryFactory.class)));
    }
}
