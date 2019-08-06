package io.sentry.config.location;

import io.sentry.config.ResourceLoader;
import io.sentry.util.Nullable;

/**
 * Tries to find the Sentry configuration file.
 */
public interface ConfigurationResourceLocator {
    /**
     * Tries to find the location of the resource containing the Sentry configuration file.
     *
     * @return the location on which some {@link ResourceLoader} can find the configuration file or null if this
     * locator could not find any.
     */
    @Nullable String getConfigurationResourcePath();
}
