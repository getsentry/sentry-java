package net.kencochrane.raven;

import net.kencochrane.raven.dsn.Dsn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Factory in charge of creating {@link Raven} instances.
 * <p>
 * The factories register themselves through the {@link ServiceLoader} system.
 * </p>
 */
public abstract class RavenFactory {
    private static final ServiceLoader<RavenFactory> AUTO_REGISTERED_FACTORIES = ServiceLoader.load(RavenFactory.class);
    private static final Map<String, RavenFactory> MANUALLY_REGISTERED_FACTORIES = new HashMap<String, RavenFactory>();
    private static final Logger logger = LoggerFactory.getLogger(RavenFactory.class);

    /**
     * Manually adds a RavenFactory to the system.
     * <p>
     * Usually RavenFactories are automatically detected with the {@link ServiceLoader} system, but some systems
     * such as Android do not provide a fully working ServiceLoader.<br />
     * If the factory isn't detected automatically, it's possible to add it through this method.
     * </p>
     *
     * @param ravenFactory ravenFactory to support.
     */
    public static void registerFactory(RavenFactory ravenFactory) {
        MANUALLY_REGISTERED_FACTORIES.put(ravenFactory.getClass().getName(), ravenFactory);
    }

    /**
     * Creates an instance of Raven using the DSN obtain through {@link net.kencochrane.raven.dsn.Dsn#dsnLookup()}.
     *
     * @return an instance of Raven.
     */
    public static Raven ravenInstance() {
        return ravenInstance(Dsn.dsnLookup());
    }

    /**
     * Creates an instance of Raven using the provided DSN.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an instance of Raven.
     */
    public static Raven ravenInstance(String dsn) {
        return ravenInstance(new Dsn(dsn));
    }

    /**
     * Creates an instance of Raven using the provided DSN.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an instance of Raven.
     */
    public static Raven ravenInstance(Dsn dsn) {
        return ravenInstance(dsn, null);
    }

    /**
     * Creates an instance of Raven using the provided DSN and the specified factory.
     *
     * @param dsn              Data Source Name of the Sentry server.
     * @param ravenFactoryName name of the raven factory to use to generate an instance of Raven.
     * @return an instance of Raven.
     */
    public static Raven ravenInstance(Dsn dsn, String ravenFactoryName) {
        Raven raven = ravenInstanceFromManualFactories(dsn, ravenFactoryName);

        if (raven == null)
            raven = ravenInstanceFromAutoFactories(dsn, ravenFactoryName);

        if (raven != null)
            return raven;
        else
            throw new IllegalStateException("Couldn't create a raven instance of '" + ravenFactoryName
                    + "' for '" + dsn + "'");
    }

    private static Raven ravenInstanceFromManualFactories(Dsn dsn, String ravenFactoryName) {
        Raven raven = null;
        if (ravenFactoryName != null && MANUALLY_REGISTERED_FACTORIES.containsKey(ravenFactoryName)) {
            RavenFactory ravenFactory = MANUALLY_REGISTERED_FACTORIES.get(ravenFactoryName);
            logger.info("Found an appropriate Raven factory for '{}': '{}'", ravenFactoryName, ravenFactory);
            raven = ravenFactory.createRavenInstance(dsn);
            if (raven == null)
                logger.warn("The raven factory '{}' couldn't create an instance of Raven", ravenFactory);
        } else if (ravenFactoryName == null) {
            for (RavenFactory ravenFactory : MANUALLY_REGISTERED_FACTORIES.values()) {
                logger.info("Found an available Raven factory: '{}'", ravenFactory);
                raven = ravenFactory.createRavenInstance(dsn);
                if (raven != null) {
                    break;
                } else {
                    logger.warn("The raven factory '{}' couldn't create an instance of Raven", ravenFactory);
                }
            }
        }
        return raven;
    }

    private static Raven ravenInstanceFromAutoFactories(Dsn dsn, String ravenFactoryName) {
        Raven raven = null;
        for (RavenFactory ravenFactory : AUTO_REGISTERED_FACTORIES) {
            if (ravenFactoryName != null && !ravenFactoryName.equals(ravenFactory.getClass().getName()))
                continue;

            logger.info("Found an appropriate Raven factory for '{}': '{}'", ravenFactoryName, ravenFactory);
            raven = ravenFactory.createRavenInstance(dsn);
            if (raven != null) {
                break;
            } else {
                logger.warn("The raven factory '{}' couldn't create an instance of Raven", ravenFactory);
            }
        }
        return raven;
    }

    /**
     * Creates an instance of Raven given a DSN.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an instance of Raven or {@code null} if it isn't possible to create one.
     */
    public abstract Raven createRavenInstance(Dsn dsn);
}
