package net.kencochrane.raven.log4j;

import net.kencochrane.raven.spi.RavenMDC;

final class Utils {

    static void initMDC() {
        if (RavenMDC.getInstance() != null) {
            if (!(RavenMDC.getInstance() instanceof Log4jMDC)) {
                throw new IllegalStateException("An incompatible RavenMDC "
                        + "instance has been set. Please check your Raven "
                        + "configuration.");
            }
            return;
        }
        RavenMDC.setInstance(new Log4jMDC());
    }

    /**
     * Prevent instantiation.
     */
    private Utils() {
    }

}
