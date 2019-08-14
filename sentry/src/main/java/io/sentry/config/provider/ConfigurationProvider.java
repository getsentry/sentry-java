package io.sentry.config.provider;

import io.sentry.util.Nullable;

/**
 * Sentry is able to load configuration from various sources. This interface is implemented by each one of them.
 */
public interface ConfigurationProvider {

    /**
     * Returns the value of the configuration property with the provided key.
     *
     * @param key the name of the configuration property
     * @return the value of the property as found in this provider or null if none found
     */
    @Nullable String getProperty(String key);
}
