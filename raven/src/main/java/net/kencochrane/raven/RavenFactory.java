package net.kencochrane.raven;

import net.kencochrane.raven.dsn.Dsn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.ServiceLoader;

/**
 * Factory in charge of creating {@link Raven} instances.
 * <p>
 * The factories register themselves through the {@link ServiceLoader} system.
 * </p>
 */
public abstract class RavenFactory {
    private static final ServiceLoader<RavenFactory> AUTO_REGISTERED_FACTORIES = ServiceLoader.load(RavenFactory.class);
    private static final Collection<RavenFactory> MANUALLY_REGISTERED_FACTORIES = new LinkedList<RavenFactory>();
    private static final Logger logger = LoggerFactory.getLogger(RavenFactory.class);

    public static void registerFactory(RavenFactory ravenFactory) {
        MANUALLY_REGISTERED_FACTORIES.add(ravenFactory);
    }

    public static Raven ravenInstance() {
        return ravenInstance(Dsn.dsnLookup());
    }

    public static Raven ravenInstance(String dsn) {
        return ravenInstance(new Dsn(dsn));
    }

    public static Raven ravenInstance(Dsn dsn) {
        return ravenInstance(dsn, null);
    }

    public static Raven ravenInstance(Dsn dsn, String ravenFactoryName) {
        for (RavenFactory ravenFactory : MANUALLY_REGISTERED_FACTORIES) {
            if (ravenFactoryName != null && !ravenFactoryName.equals(ravenFactory.getClass().getName()))
                continue;

            logger.info("Found an appropriate Raven factory for '{}': '{}'", ravenFactoryName, ravenFactory);
            Raven raven = ravenFactory.createRavenInstance(dsn);
            if (raven != null) {
                return raven;
            } else {
                logger.warn("The raven factory '{}' couldn't create an instance of Raven", ravenFactory);
            }
        }

        for (RavenFactory ravenFactory : AUTO_REGISTERED_FACTORIES) {
            if (ravenFactoryName != null && !ravenFactoryName.equals(ravenFactory.getClass().getName()))
                continue;

            logger.info("Found an appropriate Raven factory for '{}': '{}'", ravenFactoryName, ravenFactory);
            Raven raven = ravenFactory.createRavenInstance(dsn);
            if (raven != null) {
                return raven;
            } else {
                logger.warn("The raven factory '{}' couldn't create an instance of Raven", ravenFactory);
            }
        }

        throw new IllegalStateException("Couldn't create a raven instance of '" + ravenFactoryName
                + "' for '" + dsn + "'");
    }

    public abstract Raven createRavenInstance(Dsn dsn);
}
