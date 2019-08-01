package io.sentry;

import io.sentry.config.ResourceLoader;

/**
 * SentryOptions used to configure the Sentry SDK.
 */
public class SentryOptions {
    private SentryClientFactory sentryClientFactory;
    private String dsn;
    private ResourceLoader resourceLoader;

    /**
     * Gets the optionally set {@Link SentryClientFactory}.
     * @return
     */
    public SentryClientFactory getSentryClientFactory() {
        return sentryClientFactory;
    }

    /**
     * Sets the {@link SentryClientFactory} to be used when initializing the SDK.
     * @param sentryClientFactory
     */
    public void setSentryClientFactory(SentryClientFactory sentryClientFactory) {
        this.sentryClientFactory = sentryClientFactory;
    }

    /**
     * Gets the DSN. If not DSN was set, the SDK will attempt to find one in the environment.
     * @return Data Source Name.
     */
    public String getDsn() {
        return dsn;
    }

    /**
     * Sets the DSN to be used by the {@link SentryClient}.
     * @param dsn
     */
    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    /**
     * Gets the {@link ResourceLoader} to be used when looking for properties.
     * @return
     */
    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    /**
     * Sets the {@link ResourceLoader} to be used when looking for properties.
     * @param resourceLoader
     */
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
