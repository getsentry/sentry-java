package io.sentry;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.sentry.config.CompoundResourceLoader;
import io.sentry.config.ContextClassLoaderResourceLoader;
import io.sentry.config.FileResourceLoader;
import io.sentry.config.Lookup;
import io.sentry.config.ResourceLoader;
import io.sentry.config.location.CompoundResourceLocator;
import io.sentry.config.location.ConfigurationResourceLocator;
import io.sentry.config.location.EnvironmentBasedLocator;
import io.sentry.config.location.StaticFileLocator;
import io.sentry.config.location.SystemPropertiesBasedLocator;
import io.sentry.config.provider.CompoundConfigurationProvider;
import io.sentry.config.provider.ConfigurationProvider;
import io.sentry.config.provider.EnvironmentConfigurationProvider;
import io.sentry.config.provider.JndiConfigurationProvider;
import io.sentry.config.provider.JndiSupport;
import io.sentry.config.provider.LocatorBasedConfigurationProvider;
import io.sentry.config.provider.SystemPropertiesConfigurationProvider;
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

    private final Lookup lookup;
    private final Dsn dsn;
    private final SentryClientFactory clientFactory;

    /**
     * Creates a new instance of the options using the provided parameters.
     *
     * @param lookup        the lookup to locate the configuration with
     * @param dsn           the dsn to use
     * @param clientFactory the client factory to use
     * @throws NullPointerException if any of the parameters is null
     */
    public SentryOptions(Lookup lookup, Dsn dsn, SentryClientFactory clientFactory) {
        this.lookup = requireNonNull(lookup, "lookup");
        this.dsn = requireNonNull(dsn, "dsn");
        this.clientFactory = requireNonNull(clientFactory, "clientFactory");
    }

    /**
     * Creates new options using the provided lookup instance. The DSN and the client factory are obtained using the
     * provided lookup instance.
     *
     * @param lookup the lookup to locate the configuration with
     * @return new instance of SentryOptions
     */
    public static SentryOptions from(Lookup lookup) {
        return from(lookup, (String) null, null);
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
        return from(lookup, resolveDsn(lookup, dsn), factory);
    }

    private static SentryOptions from(Lookup lookup, Dsn dsn, @Nullable SentryClientFactory factory) {
        if (factory == null) {
            factory = SentryClientFactory.instantiateFrom(lookup, dsn);
        }

        if (factory == null) {
            logger.error("Failed to find a Sentry client factory in the provided configuration. Will continue"
                    + " with a dummy implementation that will send no data.");

            factory = new InvalidSentryClientFactory();
        }

        return new SentryOptions(lookup, dsn, factory);
    }

    /**
     * A convenience method to load the default SentryOptions using the default lookup instance.
     *
     * @see #getDefaultLookup()
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
     * @see #getDefaultLookup()
     *
     * @return the default sentry options with the provided DSN
     */
    public static SentryOptions defaults(@Nullable String dsn) {
        return from(getDefaultLookup(), dsn, null);
    }

    /**
     * In the default lookup returned from this method, the configuration properties are looked up in the sources in the
     * following order.
     *
     * <ol>
     * <li>JNDI, if available
     * <li>Java System Properties
     * <li>System Environment Variables
     * <li>DSN options, if a non-null DSN is provided
     * <li>Sentry properties file found in resources
     * </ol>
     *
     * @return the default lookup instance
     */
    public static Lookup getDefaultLookup() {
        return new Lookup(new CompoundConfigurationProvider(getDefaultHighPriorityConfigurationProviders()),
                new CompoundConfigurationProvider(getDefaultLowPriorityConfigurationProviders()));
    }

    /**
     * Similar to {@link #with(Dsn)} but uses just the string representation of the DSN. If the provided DSN is null,
     * the one from the current instance is used.
     *
     * @param newDsn the DSN to use or null if it should remain the same.
     * @return the new options instance (or this instance if the provided dsn is null)
     */
    public SentryOptions withDsn(@Nullable String newDsn) {
        return newDsn == null ? this : with(new Dsn(newDsn));
    }

    /**
     * Returns a copy of this options instance with the dsn set to the provided value.
     *
     * @param newDsn the dsn to use in the new options instance
     * @return the new options instance
     */
    public SentryOptions with(Dsn newDsn) {
        return from(getLookup(), requireNonNull(newDsn), null);
    }

    /**
     * Returns a copy of this options instance with the lookup set to the provided value.
     *
     * @param newLookup the lookup to use in the new options instance
     * @return the new options instance
     */
    public SentryOptions with(Lookup newLookup) {
        return from(requireNonNull(newLookup), getDsn(), null);
    }

    /**
     * Returns a copy of this options instance with the client factory set to the provided value.
     *
     * @param newClientFactory the client factory to use in the new options instance
     * @return the new options instance
     */
    public SentryOptions with(SentryClientFactory newClientFactory) {
        return from(getLookup(), getDsn(), requireNonNull(newClientFactory));
    }

    /**
     * Gets the value of the configuration key using the {@link #getLookup() lookup} and {@link #getDsn() DSN}.
     *
     * @param key the name of the configuration key
     * @return the value of the configuration key or null if not found
     */
    @Nullable
    public String getConfigurationKey(String key) {
        return lookup.get(key, dsn);
    }

    /**
     * Gets the lookup instance used by this options instance.
     *
     * @return the configured lookup instance
     */
    public Lookup getLookup() {
        return lookup;
    }

    /**
     * Gets the DSN instance used by this options instance.
     *
     * @return the configured DSN instance
     */
    public Dsn getDsn() {
        return dsn;
    }

    /**
     * Gets the Sentry client factory instance used by this options instance.
     * @return the configured client factory instance
     */
    public SentryClientFactory getClientFactory() {
        return clientFactory;
    }

    /**
     * Returns a new Sentry client obtained from the {@link #getClientFactory() factory}.
     *
     * @return the new sentry client or null if the client factory is invalid
     */
    @Nullable
    public SentryClient createClient() {
        return getClientFactory().createSentryClient(getDsn());
    }

    private static Dsn resolveDsn(Lookup lookup, @Nullable String dsn) {
        try {
            if (Util.isNullOrEmpty(dsn)) {
                dsn = Dsn.dsnFrom(lookup);
            }

            return new Dsn(dsn);
        } catch (Exception e) {
            logger.error("Error creating valid DSN from: '{}'.", dsn, e);
            throw e;
        }
    }

    // the below is support for instantiating the default lookup

    private static List<ConfigurationResourceLocator> getDefaultResourceLocators() {
        return asList(new SystemPropertiesBasedLocator(), new EnvironmentBasedLocator(), new StaticFileLocator());
    }

    private static List<ConfigurationProvider> getDefaultHighPriorityConfigurationProviders() {
        boolean jndiPresent = JndiSupport.isAvailable();

        @SuppressWarnings("checkstyle:MagicNumber")
        List<ConfigurationProvider> providers = new ArrayList<>(jndiPresent ? 3 : 2);

        if (jndiPresent) {
            providers.add(new JndiConfigurationProvider());
        }

        providers.add(new SystemPropertiesConfigurationProvider());
        providers.add(new EnvironmentConfigurationProvider());

        return providers;
    }

    private static List<ResourceLoader> getDefaultResourceLoaders() {
        return Arrays.asList(new FileResourceLoader(), new ContextClassLoaderResourceLoader());
    }

    private static List<ConfigurationProvider> getDefaultLowPriorityConfigurationProviders() {
        try {
            return singletonList((ConfigurationProvider)
                    new LocatorBasedConfigurationProvider(new CompoundResourceLoader(getDefaultResourceLoaders()),
                            new CompoundResourceLocator(getDefaultResourceLocators()), Charset.defaultCharset()));
        } catch (IOException e) {
            logger.debug("Failed to instantiate resource locator-based configuration provider.", e);
            return emptyList();
        }
    }

    /**
     * A dummy factory used in case of invalid configuration.
     */
    private static final class InvalidSentryClientFactory extends SentryClientFactory {
        /**
         * Returns null.
         *
         * @param dsn not used
         * @return always null
         */
        @Override
        public SentryClient createSentryClient(Dsn dsn) {
            return null;
        }
    }
}
