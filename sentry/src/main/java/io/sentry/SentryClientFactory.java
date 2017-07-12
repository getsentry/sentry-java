package io.sentry;

import io.sentry.config.Lookup;
import io.sentry.dsn.Dsn;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory in charge of creating {@link SentryClient} instances.
 */
public abstract class SentryClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(SentryClientFactory.class);

    /**
     * Creates an instance of Sentry by discovering the DSN.
     *
     * @return an instance of Sentry.
     */
    public static SentryClient sentryClient() {
        return sentryClient(null, null);
    }

    /**
     * Creates an instance of Sentry using the provided DSN.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an instance of Sentry.
     */
    public static SentryClient sentryClient(String dsn) {
        return sentryClient(dsn, null);
    }

    /**
     * Creates an instance of Sentry using the provided DSN and the specified factory.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @param sentryClientFactory SentryClientFactory instance to use, or null to do a config lookup.
     * @return SentryClient instance, or null if one couldn't be constructed.
     */
    public static SentryClient sentryClient(String dsn, SentryClientFactory sentryClientFactory) {
        Dsn realDsn = resolveDsn(dsn);

        // If the caller didn't pass a factory, try to look one up
        if (sentryClientFactory == null) {
            String sentryClientFactoryName = Lookup.lookup("factory", realDsn);
            if (Util.isNullOrEmpty(sentryClientFactoryName)) {
                // no name specified, use the default factory
                sentryClientFactory = new DefaultSentryClientFactory();
            } else {
                // attempt to construct the user specified factory class
                Class<? extends SentryClientFactory> factoryClass = null;
                try {
                    factoryClass = (Class<? extends SentryClientFactory>) Class.forName(sentryClientFactoryName);
                    sentryClientFactory = factoryClass.newInstance();
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                    logger.error("Error creating SentryClient using factory class: '"
                        + sentryClientFactoryName + "'.", e);
                    return null;
                }
            }
        }

        return sentryClientFactory.createSentryClient(realDsn);
    }

    private static Dsn resolveDsn(String dsn) {
        try {
            if (Util.isNullOrEmpty(dsn)) {
                dsn = Dsn.dsnLookup();
            }

            return new Dsn(dsn);
        } catch (Exception e) {
            logger.error("Error creating valid DSN from: '{}'.", dsn, e);
            throw e;
        }
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
