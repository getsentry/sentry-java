package io.sentry;

import static java.util.Objects.requireNonNull;

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
 * @see SentryClientFactory#instantiateFrom(Lookup, String)
 */
public abstract class SentryClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(SentryClientFactory.class);

    /**
     * The {@link Lookup} instance to use for obtaining configuration.
     */
    protected final Lookup lookup;

    /**
     * Creates a new instance using the provided lookup.
     *
     * @param lookup the lookup to use
     */
    protected SentryClientFactory(Lookup lookup) {
        this.lookup = requireNonNull(lookup);
    }

    /**
     * Do not use this constructor. This depends on the deprecated static initialization of the {@link Lookup} instance.
     * @deprecated use {@link #SentryClientFactory(Lookup)} instead
     */
    @Deprecated
    protected SentryClientFactory() {
        this(Lookup.getDefault());
    }

    /**
     * Creates an instance of Sentry by discovering the DSN.
     *
     * @return an instance of Sentry.
     * @deprecated use {@link SentryOptions sentryOptions}
     * .{@link SentryOptions#getSentryClientFactory() getClientFactory()}.{@link #createClient(String)} instead
     */
    @Deprecated
    @Nullable
    public static SentryClient sentryClient() {
        return sentryClient(null, null);
    }

    /**
     * Creates an instance of Sentry using the provided DSN.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an instance of Sentry.
     * @deprecated use {@link SentryOptions sentryOptions}
     * .{@link SentryOptions#getSentryClientFactory() getClientFactory()}.{@link #createClient(String)} instead
     */
    @Deprecated
    @Nullable
    public static SentryClient sentryClient(@Nullable String dsn) {
        return sentryClient(dsn, null);
    }

    /**
     * Creates an instance of Sentry using the provided DSN and the specified factory.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @param sentryClientFactory SentryClientFactory instance to use, or null to do a config lookup.
     * @return SentryClient instance, or null if one couldn't be constructed.
     * @deprecated use {@link SentryOptions sentryOptions}
     * .{@link SentryOptions#getSentryClientFactory() getClientFactory()}.{@link #createClient(String)} instead
     */
    @Deprecated
    @Nullable
    public static SentryClient sentryClient(@Nullable String dsn, @Nullable SentryClientFactory sentryClientFactory) {
        Lookup lookup = Lookup.getDefault();
        String realDsn = dsnOrLookedUp(dsn, lookup);
        SentryClientFactory factory = sentryClientFactory == null
                ? instantiateFrom(lookup, realDsn)
                : sentryClientFactory;
        return factory == null ? null : factory.createClient(realDsn);
    }

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
     * @param dsn the DSN
     * @return the Sentry client factory or null if not available
     */
    @Nullable
    public static SentryClientFactory instantiateFrom(Lookup lookup, @Nullable String dsn) {
        Dsn realDsn = new Dsn(dsnOrLookedUp(dsn, lookup));

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

    private static String dsnOrLookedUp(@Nullable String dsn, Lookup lookup) {
        if (dsn == null) {
            dsn = Dsn.dsnFrom(lookup);
        }

        return dsn;
    }

    /**
     * Creates an instance of Sentry given a DSN.
     *
     * @param dsn Data Source Name of the Sentry server to override the configuration of the factory
     * @return an instance of Sentry.
     * @throws RuntimeException when an instance couldn't be created.
     *
     * @deprecated use {@link #createClient(String)}
     */
    @Deprecated
    public abstract SentryClient createSentryClient(Dsn dsn);

    /**
     * Creates an instance of Sentry given a DSN.
     *
     * @param dsn Data Source Name of the Sentry server to override the configuration of the factory
     * @return an instance of Sentry.
     * @throws RuntimeException when an instance couldn't be created.
     */
    public SentryClient createClient(@Nullable String dsn) {
        Dsn realDsn = new Dsn(dsn == null ? Dsn.dsnFrom(lookup) : dsn);
        return createSentryClient(realDsn);
    }

    @Override
    public String toString() {
        return "SentryClientFactory{"
                + "name='" + this.getClass().getName() + '\''
                + '}';
    }
}
