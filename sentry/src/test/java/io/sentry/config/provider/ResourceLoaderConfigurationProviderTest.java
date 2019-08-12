package io.sentry.config.provider;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import io.sentry.config.ResourceLoader;
import org.junit.jupiter.api.Test;

class ResourceLoaderConfigurationProviderTest {

    @Test
    void testLoadsPropertiesFromResourceLoader() throws Exception {
        // given
        ResourceLoader resourceLoader = mock(ResourceLoader.class);

        byte[] data = UTF_8.encode("prop1=value1\nprop2=süß_žluťoučký_лошадь\n").array();
        when(resourceLoader.getInputStream("config.file")).thenReturn(new ByteArrayInputStream(data));

        ResourceLoaderConfigurationProvider provider = new ResourceLoaderConfigurationProvider(resourceLoader,
                "config.file", UTF_8);

        // when
        String val1 = provider.getProperty("prop1");
        String val2 = provider.getProperty("prop2");
        String val3 = provider.getProperty("unknown");

        // then
        assertEquals("value1", val1);
        assertEquals("süß_žluťoučký_лошадь", val2);
        assertNull(val3);
    }

    @Test
    void testUnknownFileSuppliesNullPropertyValues() throws Exception {
        // given
        ResourceLoader resourceLoader = mock(ResourceLoader.class);

        // when
        ResourceLoaderConfigurationProvider provider = new ResourceLoaderConfigurationProvider(resourceLoader,
                "unknown", UTF_8);

        // then
        assertNull(provider.getProperty("prop1"));
        assertNull(provider.getProperty("prop2"));
        assertNull(provider.getProperty(""));
    }
}
