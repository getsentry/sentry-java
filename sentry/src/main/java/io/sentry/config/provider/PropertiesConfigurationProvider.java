package io.sentry.config.provider;

import java.util.Properties;

import io.sentry.util.Nullable;

/**
 * A trivial wrapper to use a {@link Properties} instance as a configuration provider.
 */
public class PropertiesConfigurationProvider implements ConfigurationProvider {
    @Nullable
    private final Properties properties;

    /**
     * Instantiates a new configuration provider with the provided properties.
     *
     * @param properties the configuration properties
     */
    public PropertiesConfigurationProvider(@Nullable Properties properties) {
        this.properties = properties;
    }

    @Override
    @Nullable
    public String getProperty(String key) {
        return properties == null ? null : properties.getProperty(key);
    }
}
