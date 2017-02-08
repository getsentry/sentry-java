package com.getsentry.raven;

import com.getsentry.raven.dsn.Dsn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Factory in charge of creating {@link Raven} instances.
 * <p>
 * The factories register themselves through the {@link ServiceLoader} system.
 */
public abstract class RavenFactory {
    private static final ServiceLoader<RavenFactory> AUTO_REGISTERED_FACTORIES =
        ServiceLoader.load(RavenFactory.class, RavenFactory.class.getClassLoader());
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
        List<RavenFactory> ravenFactories = new LinkedList<>();
        ravenFactories.addAll(MANUALLY_REGISTERED_FACTORIES);
        for (RavenFactory autoRegisteredFactory : AUTO_REGISTERED_FACTORIES) {
            ravenFactories.add(autoRegisteredFactory);
        }
        return ravenFactories;
    }

    /**
     * Creates an instance of Raven using the DSN obtain through {@link com.getsentry.raven.dsn.Dsn#dsnLookup()}.
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
     * @param ravenFactoryName name of the RavenFactory to use to generate an instance of Raven.
     * @return an instance of Raven.
     * @throws IllegalStateException when no instance of Raven has been created.
     */
    public static Raven ravenInstance(Dsn dsn, String ravenFactoryName) {
        logger.debug("Attempting to find a working RavenFactory");

        // Loop through registered factories, keeping track of which classes we skip, which we try to instantiate,
        // and the last exception thrown.
        ArrayList<String> skippedFactories = new ArrayList<>();
        ArrayList<String> triedFactories = new ArrayList<>();
        RuntimeException lastExc = null;

        for (RavenFactory ravenFactory : getRegisteredFactories()) {
            String name = ravenFactory.getClass().getName();
            if (ravenFactoryName != null && !ravenFactoryName.equals(name)) {
                skippedFactories.add(name);
                continue;
            }

            logger.debug("Attempting to use '{}' as a RavenFactory.", ravenFactory);
            triedFactories.add(name);
            try {
                Raven ravenInstance = ravenFactory.createRavenInstance(dsn);
                logger.debug("The RavenFactory '{}' created an instance of Raven.", ravenFactory);
                return ravenInstance;
            } catch (RuntimeException e) {
                lastExc = e;
                logger.debug("The RavenFactory '{}' couldn't create an instance of Raven.", ravenFactory, e);
            }
        }

        if (ravenFactoryName != null && triedFactories.isEmpty()) {
            try {
                // see if the provided class exists on the classpath at all
                Class.forName(ravenFactoryName);
                logger.error(
                    "The RavenFactory class '{}' was found on your classpath but was not "
                    + "registered with Raven, see: "
                    + "https://github.com/getsentry/raven-java/#custom-ravenfactory", ravenFactoryName);
            } catch (ClassNotFoundException e) {
                logger.error("The RavenFactory class name '{}' was specified but "
                    + "the class was not found on your classpath.", ravenFactoryName);
            }
        }

        // Throw an IllegalStateException that attempts to be helpful.
        StringBuilder sb = new StringBuilder();
        sb.append("Couldn't create a raven instance for: '");
        sb.append(dsn);
        sb.append('\'');
        if (ravenFactoryName != null) {
            sb.append("; ravenFactoryName: ");
            sb.append(ravenFactoryName);

            if (skippedFactories.isEmpty()) {
                sb.append("; no skipped factories");
            } else {
                sb.append("; skipped factories: ");
                String delim = "";
                for (String skippedFactory : skippedFactories) {
                    sb.append(delim);
                    sb.append(skippedFactory);
                    delim = ", ";
                }
            }
        }

        if (triedFactories.isEmpty()) {
            sb.append("; no factories tried!");
            throw new IllegalStateException(sb.toString());
        }

        sb.append("; tried factories: ");
        String delim = "";
        for (String triedFactory : triedFactories) {
            sb.append(delim);
            sb.append(triedFactory);
            delim = ", ";
        }

        sb.append("; cause contains exception thrown by the last factory tried.");
        throw new IllegalStateException(sb.toString(), lastExc);
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
