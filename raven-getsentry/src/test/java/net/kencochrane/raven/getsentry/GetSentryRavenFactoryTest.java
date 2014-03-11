package net.kencochrane.raven.getsentry;

import net.kencochrane.raven.RavenFactory;
import org.hamcrest.Matchers;
import org.testng.annotations.Test;

import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

public class GetSentryRavenFactoryTest {
    @Test
    public void checkServiceLoaderProvidesFactory() throws Exception {
        ServiceLoader<RavenFactory> ravenFactories = ServiceLoader.load(RavenFactory.class);

        assertThat(ravenFactories, Matchers.<RavenFactory>hasItem(instanceOf(GetSentryRavenFactory.class)));
    }
}
