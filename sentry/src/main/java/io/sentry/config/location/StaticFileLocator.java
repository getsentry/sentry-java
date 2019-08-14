package io.sentry.config.location;

/**
 * Provides the configuration file location using a file name provided at instantiation time.
 */
public class StaticFileLocator implements ConfigurationResourceLocator {
    /**
     * The default file name of the Sentry configuration file.
     */
    public static final String DEFAULT_FILE_PATH = "sentry.properties";

    private final String path;

    /**
     * Constructs a new instance using the {@link #DEFAULT_FILE_PATH}.
     */
    public StaticFileLocator() {
        this(DEFAULT_FILE_PATH);
    }

    /**
     * Constructs a new instance that will return the provided path as the path of the Sentry configuration.
     *
     * @param filePath the path to the Sentry configuration
     */
    public StaticFileLocator(String filePath) {
        this.path = filePath;
    }

    @Override
    public String getConfigurationResourcePath() {
        return path;
    }
}
