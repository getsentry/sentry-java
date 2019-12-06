package io.sentry;

import static java.util.Objects.requireNonNull;

import io.sentry.config.Lookup;
import io.sentry.config.ResourceLoader;
import io.sentry.dsn.Dsn;
import io.sentry.util.Nullable;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SentryOptions used to configure the Sentry SDK.
 */
public final class SentryOptions {
    private static final Logger logger = LoggerFactory.getLogger(SentryOptions.class);

    private Lookup lookup;
    private SentryClientFactory sentryClientFactory;
    private String dsn;

    /**
     * The resource loader to use during {@link Lookup#getDefault()}, if an instance of {@code SentryOptions} is first
     * passed to {@link Sentry#init(SentryOptions)}.
     *
     * @deprecated this is just in support of the hack to use a custom resource loader during the deprecated static
     * lookup
     */
    @Deprecated
    private ResourceLoader resourceLoader;

    /**
     * Creates a new instance of the options using the provided parameters.
     *
     * @param lookup        the lookup to locate the configuration with
     * @param dsn           the DSN to use or null if the DSN should be found in the lookup
     * @param sentryClientFactory the client factory to use or null if the instance should be found in the lookup
     * @throws NullPointerException if lookup is null
     */
    public SentryOptions(Lookup lookup, @Nullable String dsn, @Nullable SentryClientFactory sentryClientFactory) {
        this.lookup = requireNonNull(lookup, "lookup");
        this.dsn = resolveDsn(lookup, dsn);
        this.sentryClientFactory = sentryClientFactory == null
                ? SentryClientFactory.instantiateFrom(this.lookup, this.dsn)
                : sentryClientFactory;
        this.resourceLoader = null;

        if (this.sentryClientFactory == null) {
            logger.error("Failed to find a Sentry client factory in the provided configuration. Will continue"
                    + " with a dummy implementation that will send no data.");

            this.sentryClientFactory = new InvalidSentryClientFactory();
        }
    }

    /**
     * Creates new options using the provided lookup instance. The DSN and the client factory are obtained using the
     * provided lookup instance.
     *
     * @param lookup the lookup to locate the configuration with
     * @return new instance of SentryOptions
     */
    public static SentryOptions from(Lookup lookup) {
        return from(lookup, null, null);
    }

    /**
     * Creates new options using the provided lookup instance and optional DSN. If the provided DSN is null, the DSN
     * to use is looked up using the provided lookup instance.
     *
     * @param lookup the lookup to locate the configuration with
     * @param dsn    the optional dsn to use
     * @return new instance of SentryOptions
     */
    public static SentryOptions from(Lookup lookup, @Nullable String dsn) {
        return from(lookup, dsn, null);
    }

    /**
     * Creates new options using the provided lookup instance and optional DSN and client factory. If the provided
     * DSN or client factory is null, the DSN and client factory to use is looked up using the provided lookup instance.
     *
     * @param lookup  the lookup to locate the configuration with
     * @param dsn     the optional dsn to use or null if the value should be located in the config
     * @param factory the client factory to use or null if the value should be located in the config
     * @return new instance of SentryOptions
     */
    public static SentryOptions from(Lookup lookup, @Nullable String dsn, @Nullable SentryClientFactory factory) {
        return new SentryOptions(lookup, dsn, factory);
    }

    /**
     * A convenience method to load the default SentryOptions using the default lookup instance.
     *
     * @see Lookup#getDefault()
     *
     * @return the default sentry options
     */
    public static SentryOptions defaults() {
        return defaults(null);
    }

    /**
     * A convenience method to load the default SentryOptions using the default lookup instance using the provided
     * DSN instead of the one from the config.
     *
     * <p>Equivalent to {@code SentryOptions.defaults().withDsn(dsn)}.
     *
     * @param dsn the DSN to override the configured default with
     *
     * @see Lookup#getDefault()
     *
     * @return the default sentry options with the provided DSN
     */
    public static SentryOptions defaults(@Nullable String dsn) {
        return from(Lookup.getDefault(), dsn, null);
    }

    /**
     * Gets the optionally set {@link SentryClientFactory}.
     * @return {@link SentryClientFactory}
     */
    public SentryClientFactory getSentryClientFactory() {
        return sentryClientFactory;
    }

    /**
     * Sets the {@link SentryClientFactory} to be used when initializing the SDK.
     * @param clientFactory Factory used to create a {@link SentryClient}.
     */
    public void setSentryClientFactory(@Nullable SentryClientFactory clientFactory) {
        this.sentryClientFactory = clientFactory == null
                ? SentryClientFactory.instantiateFrom(getLookup(), getDsn())
                : clientFactory;
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
     * @param dsn Sentry Data Source Name.
     */
    public void setDsn(@Nullable String dsn) {
        this.dsn = resolveDsn(getLookup(), dsn);
    }

    /**
     * Returns the lookup instance to use.
     * @return the lookup instance
     */
    public Lookup getLookup() {
        return lookup;
    }

    /**
     * Sets the lookup instance to use.
     * @param lookup the lookup to use
     */
    public void setLookup(Lookup lookup) {
        this.lookup = requireNonNull(lookup);
    }

    /**
     * Gets the resource loader to be used during {@link Sentry#init(SentryOptions)} to set the
     * {@link Sentry#getResourceLoader()}. This is kept for supporting the initialization of a default lookup instance
     * ({@link Lookup#getDefault()}) that is used during the deprecated static lookups ({@link Lookup#lookup(String)}
     * and {@link Lookup#lookup(String, Dsn)}).
     *
     * @deprecated don't use this method. Instead configure the {@link Lookup} instance with appropriate
     * {@link io.sentry.config.provider.ResourceLoaderConfigurationProvider}s and create a new {@link SentryOptions}
     * instance
     * @return the resource loader
     */
    @Deprecated
    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    /**
     * Sets the resource loader to be used during {@link Sentry#init(SentryOptions)} to set the
     * {@link Sentry#getResourceLoader()}. This is kept for supporting the initialization of a default lookup instance
     * ({@link Lookup#getDefault()}) that is used during the deprecated static lookups ({@link Lookup#lookup(String)}
     * and {@link Lookup#lookup(String, Dsn)}).
     *
     * @param resourceLoader the resource loader to use in default lookup instance
     *
     * @deprecated don't use this method. Instead configure the {@link Lookup} instance with appropriate
     * {@link io.sentry.config.provider.ResourceLoaderConfigurationProvider}s and create a new {@link SentryOptions}
     * instance
     */
    @Deprecated
    public void setResourceLoader(@Nullable ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    private static String resolveDsn(Lookup lookup, @Nullable String dsn) {
        try {
            if (Util.isNullOrEmpty(dsn)) {
                dsn = Dsn.dsnFrom(lookup);
            }

            return dsn;
        } catch (RuntimeException e) {
            logger.error("Error creating valid DSN from: '{}'.", dsn, e);
            throw e;
        }
    }

    /**
     * A dummy factory used in case of invalid configuration.
     */
    private final class InvalidSentryClientFactory extends SentryClientFactory {

        private InvalidSentryClientFactory() {
            super(getLookup());
        }

        /**
         * Returns null.
         *
         * @param newDsn not used
         * @return always null
         */
        @Override
        public SentryClient createSentryClient(Dsn newDsn) {
            return null;
        }
    }
}
