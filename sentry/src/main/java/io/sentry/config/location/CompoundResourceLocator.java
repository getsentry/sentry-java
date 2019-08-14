package io.sentry.config.location;

import java.util.Collection;

import io.sentry.util.Nullable;

/**
 * Wraps multiple resource locators and returns the first non-null configuration file path.
 */
public class CompoundResourceLocator implements ConfigurationResourceLocator {
    private final Collection<ConfigurationResourceLocator> locators;

    /**
     * Instantiates a new compound configuration resource locator.
     * @param locators the locators to iterate through
     */
    public CompoundResourceLocator(Collection<ConfigurationResourceLocator> locators) {
        this.locators = locators;
    }

    /**
     * Tries to find the location of the resource containing the Sentry configuration file.
     *
     * @return the first non-null configuration file path (in the iteration order of the collection provided to
     * the constructor) or null if none such exists.
     */
    @Override
    @Nullable
    public String getConfigurationResourcePath() {
        for (ConfigurationResourceLocator l : locators) {
            String path = l.getConfigurationResourcePath();
            if (path != null) {
                return path;
            }
        }

        return null;
    }
}
