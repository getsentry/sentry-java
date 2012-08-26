package net.kencochrane.raven.spi;

import java.util.ServiceLoader;

public abstract class RavenMDC {

    private static RavenMDC instance;

    public static RavenMDC getInstance() {
        synchronized (RavenMDC.class) {
            if (instance == null) {
                instance = ServiceLoader.load(RavenMDC.class).iterator().next();
            }
        }
        return instance;
    }

    public abstract Object get(String key);

    public abstract void put(String key, Object value);

    public abstract void remove(String key);

}
