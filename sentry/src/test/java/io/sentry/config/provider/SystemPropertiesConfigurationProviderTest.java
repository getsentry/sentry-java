package io.sentry.config.provider;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.Random;

import org.junit.Test;

public class SystemPropertiesConfigurationProviderTest {

    private Random rnd = new Random();

    @Test
    public void testCanReadFromSystemProperties() throws Exception {
        // given
        SystemPropertiesConfigurationProvider provider = new SystemPropertiesConfigurationProvider();
        String propertyName = initRandomSystemProperty(SystemPropertiesConfigurationProvider.DEFAULT_SYSTEM_PROPERTY_PREFIX);

        // when
        String val = provider.getProperty(propertyName);

        // then
        assertNotNull(val);
        assertThat(val, is("testValue"));
    }

    @Test
    public void testHonorsCustomPrefix() throws Exception {
        // given
        SystemPropertiesConfigurationProvider provider = new SystemPropertiesConfigurationProvider("custom.prefix");
        String propertyName = initRandomSystemProperty("custom.prefix");

        // when
        String val = provider.getProperty(propertyName);

        // then
        assertNotNull(val);
        assertThat(val, is("testValue"));
    }

    private String initRandomSystemProperty(String prefix) {
        String randomName = "system.properties.configuration.provider.test-" + rnd.nextLong();
        System.setProperty(prefix + randomName, "testValue");
        return randomName;
    }
}
