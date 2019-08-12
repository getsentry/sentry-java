package io.sentry.config;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.sentry.Sentry;
import io.sentry.config.location.CompoundResourceLocator;
import io.sentry.config.location.ConfigurationResourceLocator;
import io.sentry.config.location.StaticFileLocator;
import io.sentry.config.location.EnvironmentBasedLocator;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle lookup of configuration keys by trying JNDI, System Environment, and Java System Properties.
 *
 * By default (when instantiated using the default constructor), the sources from which the configuration
 * properties are consulted in the following order:
 *
 * 1. JNDI, if available
 * 2. Java System Properties
 * 3. System Environment Variables
 * 4. DSN options, if a non-null DSN is provided
 * 5. Sentry properties file found in resources
 */
public final class Lookup {
    private static final Logger logger = LoggerFactory.getLogger(Lookup.class);
    private static final Object INSTANCE_LOCK = new Object();
    private static Lookup instance;

    private final ConfigurationProvider highPriorityProvider;
    private final ConfigurationProvider lowPriorityProvider;

    /**
     * Constructs a new instance of Lookup with the default configuration providers.
     */
    public Lookup() {
        this(new CompoundConfigurationProvider(getDefaultHighPriorityConfigurationProviders()),
                new CompoundConfigurationProvider(getDefaultLowPriorityConfigurationProviders()));
    }

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
        ResourceLoader sentryLoader = Sentry.getResourceLoader();

        return sentryLoader == null
                ? Arrays.asList(new FileResourceLoader(), new ContextClassLoaderResourceLoader())
                : Arrays.asList(new FileResourceLoader(), sentryLoader, new ContextClassLoaderResourceLoader());
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

    // for use in the deprecated methods
    private static Lookup getDeprecatedInstance() {
        synchronized (INSTANCE_LOCK) {
            if (instance == null) {
                instance = new Lookup();
            }

            return instance;
        }
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
        return getDeprecatedInstance().get(key, dsn);
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
    public String get(String key, Dsn dsn) {
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
}
