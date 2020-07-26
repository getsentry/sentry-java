package io.sentry.config.provider;

import java.util.List;

/**
 * Interface for provider handling multiple sources.
 */
public interface MultiConfigurationProvider extends ConfigurationProvider {

    /**
     * Returns all the values of the configuration property with the provided key.
     *
     * @param key the name of the configuration property
     * @return an ordered list with all the values for the property
     */
    List<String> getAllProperty(String key);

}
