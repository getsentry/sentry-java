package io.sentry.config;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.sentry.Sentry;
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
import io.sentry.config.provider.MultiConfigurationProvider;
import io.sentry.config.provider.SystemPropertiesConfigurationProvider;
import io.sentry.dsn.Dsn;
import io.sentry.util.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle lookup of configuration keys from the various sources.
 *
 * @see #getDefault() for obtaining the default instance
 */
public final class Lookup {
    private static final Logger logger = LoggerFactory.getLogger(Lookup.class);

    private final ConfigurationProvider highPriorityProvider;
    private final ConfigurationProvider lowPriorityProvider;

    /**
     * Constructs a new Lookup instance.
     *
     * The two configuration providers are used before and after an attempt to load the configuration property from
     * the DSN in the {@link #get(String, Dsn)} method.
     *
     * @param highPriorityProvider the configuration provider that is consulted before the check in DSN
     * @param lowPriorityProvider the configuration provider that is consulted only after the high priority one and
     *                            the DSN
     */
    public Lookup(ConfigurationProvider highPriorityProvider, ConfigurationProvider lowPriorityProvider) {
        this.highPriorityProvider = highPriorityProvider;
        this.lowPriorityProvider = lowPriorityProvider;
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
    public static Lookup getDefault() {
        return new Lookup(
                new CompoundConfigurationProvider(
                        getDefaultHighPriorityConfigurationProviders(Collections.<ConfigurationProvider>emptyList())
                ),
                new CompoundConfigurationProvider(
                        getDefaultLowPriorityConfigurationProviders(Collections.<ConfigurationProvider>emptyList())
                )
        );
    }

    /**
     * In the default lookup returned from this method, the configuration properties are looked up in the sources in the
     * following order.
     *
     * <ol>
     * <li>Additional high priority providers</li>
     * <li>JNDI, if available
     * <li>Java System Properties
     * <li>System Environment Variables
     * <li>Additional low priority providers</li>
     * <li>DSN options, if a non-null DSN is provided
     * <li>Sentry properties file found in resources
     * </ol>
     *
     * @param highPriorityProviders the list of providers with high priority
     * @param lowPriorityProviders the list of providers with low priority
     * @return the default lookup instance
     */
    public static Lookup getDefaultWithAdditionalProviders(Collection<ConfigurationProvider> highPriorityProviders,
                                                           Collection<ConfigurationProvider> lowPriorityProviders) {
        return new Lookup(
                new CompoundConfigurationProvider(getDefaultHighPriorityConfigurationProviders(highPriorityProviders)),
                new CompoundConfigurationProvider(getDefaultLowPriorityConfigurationProviders(lowPriorityProviders))
        );
    }

    private static List<ConfigurationResourceLocator> getDefaultResourceLocators() {
        return asList(new SystemPropertiesBasedLocator(), new EnvironmentBasedLocator(), new StaticFileLocator());
    }

    private static List<ConfigurationProvider> getDefaultHighPriorityConfigurationProviders(
            Collection<ConfigurationProvider> additionalProviders
    ) {
        boolean jndiPresent = JndiSupport.isAvailable();

        @SuppressWarnings("checkstyle:MagicNumber")
        int providersCount = jndiPresent ? 3 + additionalProviders.size() : 2 + additionalProviders.size();

        List<ConfigurationProvider> providers = new ArrayList<>(providersCount);
        providers.addAll(additionalProviders);

        if (jndiPresent) {
            providers.add(new JndiConfigurationProvider());
        }

        providers.add(new SystemPropertiesConfigurationProvider());
        providers.add(new EnvironmentConfigurationProvider());

        return providers;
    }

    private static List<ResourceLoader> getDefaultResourceLoaders() {
        ResourceLoader sentryLoader = Sentry.getResourceLoader();

        return sentryLoader == null
                ? Arrays.asList(new FileResourceLoader(), new ContextClassLoaderResourceLoader())
                : Arrays.asList(new FileResourceLoader(), sentryLoader, new ContextClassLoaderResourceLoader());
    }

    private static List<ConfigurationProvider> getDefaultLowPriorityConfigurationProviders(
            Collection<ConfigurationProvider> additionalProviders
    ) {

        List<ConfigurationProvider> providers = new ArrayList<>(additionalProviders.size());
        providers.addAll(additionalProviders);

        try {
            providers.add(new LocatorBasedConfigurationProvider(new CompoundResourceLoader(getDefaultResourceLoaders()),
                            new CompoundResourceLocator(getDefaultResourceLocators()), Charset.defaultCharset()));
        } catch (IOException e) {
            logger.debug("Failed to instantiate resource locator-based configuration provider.", e);
        }
        return providers;
    }


    /**
     * Attempt to lookup a configuration key, without checking any DSN options.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @return value of configuration key, if found, otherwise null
     *
     * @deprecated obtain an instance of this class and use {@link #get(String)}
     */
    @Deprecated
    public static String lookup(String key) {
        return lookup(key, null);
    }

    /**
     * Attempt to lookup a configuration key using the following order:
     *
     * 1. JNDI, if available
     * 2. Java System Properties
     * 3. System Environment Variables
     * 4. DSN options, if a non-null DSN is provided
     * 5. Sentry properties file found in resources
     *
     * @param key name of configuration key, e.g. "dsn"
     * @param dsn an optional DSN to retrieve options from
     * @return value of configuration key, if found, otherwise null
     *
     * @deprecated obtain an instance of this class and use {@link #get(String, Dsn)}
     */
    @Deprecated
    public static String lookup(String key, Dsn dsn) {
        return getDefault().get(key, dsn);
    }

    /**
     * Attempt to lookup a configuration key, without checking any DSN options.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @return value of configuration key, if found, otherwise null
     */
    @Nullable
    public String get(String key) {
        return get(key, null);
    }

    /**
     * Attempt to lookup a configuration key using either some internal means or from the DSN.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @param dsn an optional DSN to retrieve options from
     * @return value of configuration key, if found, otherwise null
     */
    @Nullable
    public String get(String key, @Nullable Dsn dsn) {
        String val = highPriorityProvider.getProperty(key);

        if (val == null && dsn != null) {
            val = dsn.getOptions().get(key);
            if (val != null) {
                logger.debug("Found {}={} in DSN.", key, val);
            }
        }

        if (val == null) {
            val = lowPriorityProvider.getProperty(key);
        }

        return val == null ? null : val.trim();
    }

    /**
     * Attempt to lookup a configuration key using either some internal means or from the DSN.
     *
     * @param key name of configuration key, e.g. "dsn"
     * @param dsn an optional DSN to retrieve options from
     * @return all values of configuration key, if found, otherwise an empty list
     */
    public List<String> getAll(String key, @Nullable Dsn dsn) {
        List<String> result = new ArrayList<>();
        addToList(result, highPriorityProvider, key);
        if (dsn != null) {
            String val = dsn.getOptions().get(key);
            if (val != null) {
                result.add(val);
            }
        }
        addToList(result, lowPriorityProvider, key);
        return result;
    }

    private void addToList(List<String> result, ConfigurationProvider provider, String key) {
        if (provider instanceof MultiConfigurationProvider) {
            result.addAll(((MultiConfigurationProvider) provider).getAllProperty(key));
        } else {
            String val = provider.getProperty(key);
            if (val != null) {
                result.add(val);
            }
        }
    }

}
