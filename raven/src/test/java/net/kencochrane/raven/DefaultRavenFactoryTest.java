package net.kencochrane.raven;

import org.hamcrest.Matchers;
import org.testng.annotations.Test;

import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

public class DefaultRavenFactoryTest {
    @Test
    public void checkServiceLoaderProvidesFactory() throws Exception {
        ServiceLoader<RavenFactory> ravenFactories = ServiceLoader.load(RavenFactory.class);

        assertThat(ravenFactories, Matchers.<RavenFactory>hasItem(instanceOf(DefaultRavenFactory.class)));
    }
}
