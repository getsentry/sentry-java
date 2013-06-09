package net.kencochrane.raven;

import java.util.ServiceLoader;

/**
 * Factory in charge of creating {@link Raven} instances.
 * <p>
 * The factories register themselves through the {@link ServiceLoader} system.
 * </p>
 */
public abstract class RavenFactory {
    private static final ServiceLoader<RavenFactory> RAVEN_FACTORIES = ServiceLoader.load(RavenFactory.class);

    public static Raven ravenInstance(Dsn dsn) {
        for (RavenFactory ravenFactory : RAVEN_FACTORIES) {
            Raven raven = ravenFactory.createRavenInstance(dsn);
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
            if (!ravenFactoryName.equals(ravenFactory.getClass().getName()))
                continue;

            Raven raven = ravenFactory.createRavenInstance(dsn);
            if (raven != null) {
                return raven;
            }
        }

        throw new IllegalStateException("Couldn't create a raven instance for '" + dsn + "'");
    }

    protected abstract Raven createRavenInstance(Dsn dsn);
}
