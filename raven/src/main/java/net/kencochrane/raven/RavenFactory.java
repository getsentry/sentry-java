package net.kencochrane.raven;

import com.google.common.collect.Iterables;
import net.kencochrane.raven.dsn.Dsn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Factory in charge of creating {@link Raven} instances.
 * <p>
 * The factories register themselves through the {@link ServiceLoader} system.
 */
public abstract class RavenFactory {
    private static final ServiceLoader<RavenFactory> AUTO_REGISTERED_FACTORIES = ServiceLoader.load(RavenFactory.class, RavenFactory.class.getClassLoader());
    private static final Set<RavenFactory> MANUALLY_REGISTERED_FACTORIES = new HashSet<>();
    private static final Logger logger = LoggerFactory.getLogger(RavenFactory.class);

    /**
     * Manually adds a RavenFactory to the system.
     * <p>
     * Usually RavenFactories are automatically detected with the {@link ServiceLoader} system, but some systems
     * such as Android do not provide a fully working ServiceLoader.<br>
     * If the factory isn't detected automatically, it's possible to add it through this method.
     *
     * @param ravenFactory ravenFactory to support.
     */
    public static void registerFactory(RavenFactory ravenFactory) {
        MANUALLY_REGISTERED_FACTORIES.add(ravenFactory);
    }

    private static Iterable<RavenFactory> getRegisteredFactories() {
        return Iterables.concat(MANUALLY_REGISTERED_FACTORIES, AUTO_REGISTERED_FACTORIES);
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
     * @throws IllegalStateException when no instance of Raven has been created.
     */
    public static Raven ravenInstance(Dsn dsn, String ravenFactoryName) {
        //Loop through registered factories
        logger.debug("Attempting to find a working Raven factory");
        for (RavenFactory ravenFactory : getRegisteredFactories()) {
            if (ravenFactoryName != null && !ravenFactoryName.equals(ravenFactory.getClass().getName()))
                continue;

            logger.debug("Attempting to use '{}' as a Raven factory.", ravenFactory);
            try {
                Raven ravenInstance = ravenFactory.createRavenInstance(dsn);
                logger.debug("The raven factory '{}' created an instance of Raven.", ravenFactory);
                return ravenInstance;
            } catch (RuntimeException e) {
                logger.debug("The raven factory '{}' couldn't create an instance of Raven.", ravenFactory, e);
            }
        }

        throw new IllegalStateException("Couldn't create a raven instance for '" + dsn + "'");
    }

    /**
     * Creates an instance of Raven given a DSN.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an instance of Raven.
     * @throws RuntimeException when an instance couldn't be created.
     */
    public abstract Raven createRavenInstance(Dsn dsn);

    @Override
    public String toString() {
        return "RavenFactory{"
                + "name='" + this.getClass().getName() + '\''
                + '}';
    }
}
