package io.sentry.config.provider;

import java.io.IOException;
import java.nio.charset.Charset;

import io.sentry.config.ResourceLoader;
import io.sentry.config.location.ConfigurationResourceLocator;

/**
 * Similar to {@link ResourceLoaderConfigurationProvider} but uses a {@link ConfigurationResourceLocator} to find
 * the path to the configuration file.
 */
public class LocatorBasedConfigurationProvider extends ResourceLoaderConfigurationProvider {
    /**
     * Instantiates a new configuration provider using the parameters.
     *
     * @param rl the resource loader to load the contents of the configuration file with
     * @param locator the locator to find the configuration file with
     * @param charset the charset of the configuration file
     * @throws IOException on failure to process the configuration file
     */
    public LocatorBasedConfigurationProvider(ResourceLoader rl, ConfigurationResourceLocator locator, Charset charset)
            throws IOException {
        super(rl, locator.getConfigurationResourcePath(), charset);
    }
}
