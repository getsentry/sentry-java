package com.getsentry.raven;

import org.testng.annotations.Test;

import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;

public class DefaultRavenFactoryTest {
    @Test
    public void checkServiceLoaderProvidesFactory() throws Exception {
        ServiceLoader<RavenFactory> ravenFactories = ServiceLoader.load(RavenFactory.class);

        assertThat(ravenFactories, contains(instanceOf(DefaultRavenFactory.class)));
    }
}
