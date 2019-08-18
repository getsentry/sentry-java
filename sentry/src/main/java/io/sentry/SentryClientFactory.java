package io.sentry;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import io.sentry.config.Lookup;
import io.sentry.dsn.Dsn;
import io.sentry.util.Nullable;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory in charge of creating {@link SentryClient} instances. The implementations should have a constructor with a
 * single parameter of type {@link Lookup}.
 *
 * @see SentryClientFactory#instantiateFrom(Lookup, Dsn)
 */
public abstract class SentryClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(SentryClientFactory.class);

    /**
     * Creates a new instance of the configured implementation of the Sentry client factory.
     *
     * <p>The provided parameters (lookup and dsn) are used to get the class name of the client factory (by looking
     * for the configuration parameter called "factory"). If no such configuration parameter exists an instance of
     * the {@link DefaultSentryClientFactory} initialized with the provided lookup is returned.
     *
     * <p>If such configuration parameter exists, a new instance of the configured class is created by first looking
     * for a constructor accepting a single parameter of type {@link Lookup} or using a default constructor if no such
     * constructor is found. If for any reason such instantiation fails, {@code null} is returned.
     *
     * @param lookup the lookup instance to use for reading the configuration
     * @param dsn the DSN instance
     * @return the Sentry client factory or null if not available
     */
    public static @Nullable SentryClientFactory instantiateFrom(Lookup lookup, Dsn dsn) {
        Dsn realDsn = dsnOrLookedUp(dsn, lookup);

        SentryClientFactory sentryClientFactory;

        String sentryClientFactoryName = lookup.get("factory", realDsn);
        if (Util.isNullOrEmpty(sentryClientFactoryName)) {
            // no name specified, use the default factory
            sentryClientFactory = new DefaultSentryClientFactory(lookup);
        } else {
            // attempt to construct the user specified factory class
            try {
                Class<?> factoryClass = Class.forName(sentryClientFactoryName);

                Constructor<?> ctor = null;
                try {
                    ctor = factoryClass.getConstructor(Lookup.class);
                    sentryClientFactory = (SentryClientFactory) ctor.newInstance(lookup);
                } catch (NoSuchMethodException e) {
                    sentryClientFactory = (SentryClientFactory) factoryClass.newInstance();
                } catch (InvocationTargetException e) {
                    logger.warn("Failed to instantiate SentryClientFactory using " + ctor + ". Falling back to using"
                            + " the default constructor, if any.");
                    sentryClientFactory = (SentryClientFactory) factoryClass.newInstance();
                }
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                logger.error("Error creating SentryClient using factory class: '"
                        + sentryClientFactoryName + "'.", e);
                return null;
            }
        }

        return sentryClientFactory;
    }

    private static Dsn dsnOrLookedUp(@Nullable Dsn dsn, Lookup lookup) {
        if (dsn == null) {
            dsn = new Dsn(Dsn.dsnFrom(lookup));
        }

        return dsn;
    }

    /**
     * Creates an instance of Sentry given a DSN.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an instance of Sentry.
     * @throws RuntimeException when an instance couldn't be created.
     */
    public abstract SentryClient createSentryClient(Dsn dsn);

    @Override
    public String toString() {
        return "SentryClientFactory{"
            + "name='" + this.getClass().getName() + '\''
            + '}';
    }
}
