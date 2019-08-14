package io.sentry.config.location;

import io.sentry.util.Nullable;

/**
 * Tries to find the location of the Sentry configuration file using the value of some system property.
 */
public class SystemPropertiesBasedLocator implements ConfigurationResourceLocator {
    /**
     * The default system property to use for obtaining the location of the Sentry configuration file.
     */
    public static final String DEFAULT_PROPERTY_NAME = "sentry.properties.file";

    private final String propertyName;

    /**
     * Constructs a new instance that will use the {@link #DEFAULT_PROPERTY_NAME}.
     */
    public SystemPropertiesBasedLocator() {
        this(DEFAULT_PROPERTY_NAME);
    }

    /**
     * Constructs a new instance that will use the provided system property name.
     *
     * @param propertyName the name of the property to load the location of the Sentry configuration file from
     */
    public SystemPropertiesBasedLocator(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    @Nullable
    public String getConfigurationResourcePath() {
        return System.getProperty(propertyName);
    }
}
