package io.sentry.config.provider;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class EnvironmentConfigurationProviderTest {
    // the environment variables used in this test class have to be defined in the surefire configuration in pom.xml

    @Test
    public void testCanReadEnvironment() throws Exception {
        // given
        EnvironmentConfigurationProvider provider = new EnvironmentConfigurationProvider();

        // when
        String val = provider.getProperty("test.property");

        // then
        assertNotNull(val);
        assertThat(val, is(System.getenv("SENTRY_TEST_PROPERTY")));
    }

    @Test
    public void testHonorsCustomEnvVarPrefix() throws Exception {
        // given
        EnvironmentConfigurationProvider provider = new EnvironmentConfigurationProvider("CUSTOM_PREFIX_");

        // when
        String val = provider.getProperty("test.property");

        // then
        assertNotNull(val);
        assertThat(val, is(System.getenv("CUSTOM_PREFIX_TEST_PROPERTY")));
    }
}
