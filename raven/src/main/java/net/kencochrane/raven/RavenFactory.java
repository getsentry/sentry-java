package net.kencochrane.raven;

import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class RavenFactory {
    private static final Logger logger = Logger.getLogger(RavenFactory.class.getCanonicalName());
    private static final ServiceLoader<RavenFactory> RAVEN_FACTORIES = ServiceLoader.load(RavenFactory.class);

    public static Raven ravenInstance(Dsn dsn) {
        for (RavenFactory ravenFactory : RAVEN_FACTORIES) {
            Raven raven = getRavenSafely(dsn, ravenFactory);
            if (raven != null) {
                return raven;
            }
        }

        throw new IllegalStateException("Couldn't create a raven instance for '" + dsn + "'");
    }

    public static Raven ravenInstance(Dsn dsn, String ravenFactoryName) {
        if (ravenFactoryName == null)
            return ravenInstance(dsn);

        for (RavenFactory ravenFactory : RAVEN_FACTORIES) {
            if (!ravenFactoryName.equals(ravenFactory.getClass().getCanonicalName()))
                continue;

            Raven raven = getRavenSafely(dsn, ravenFactory);
            if (raven != null) {
                return raven;
            }
        }

        throw new IllegalStateException("Couldn't create a raven instance for '" + dsn + "'");
    }

    private static Raven getRavenSafely(Dsn dsn, RavenFactory ravenFactory) {
        Raven raven = null;
        try {
            raven = ravenFactory.createRavenInstance(dsn);
        } catch (Exception e) {
            logger.log(Level.WARNING, "An exception occurred during the creation of a Raven instance with "
                    + "'" + ravenFactory + "' using the DSN '" + dsn + "'", e);
        }
        return raven;
    }

    public abstract Raven createRavenInstance(Dsn dsn);
}
