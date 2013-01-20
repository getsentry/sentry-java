package net.kencochrane.raven.spi;

/**
 * Since Raven plugins may be executed on threads different from those that
 * produce logs, RavenMDC provides a means for context variables to be passed
 * from log spawning threads to log processing threads.
 *
 * This service is intended to be used by Raven plugins and not by user
 * application. An implementation is expected to be singleton and should be
 * thread-safe.
 *
 * @author vvasabi
 * @since 1.0
 */
public abstract class RavenMDC {

    private static RavenMDC instance;

    /**
     * Get the current instance.
     *
     * @return current instance
     */
    public static RavenMDC getInstance() {
        return instance;
    }

    /**
     * Set the instance of RavenMDC. Note that this method can only be called
     * once.
     *
     * @param newInstance new instance of RavenMDC
     */
    public static synchronized void setInstance(RavenMDC newInstance) {
        if (newInstance == null) {
            throw new NullPointerException("New instance cannot be null.");
        }
        if (instance != null) {
            throw new IllegalStateException("A RavenMDC instance already exists.");
        }
        instance = newInstance;
    }

    /**
     * Get the context variable specified by key from MDC. If the value
     * specified does not exist, null is returned.
     *
     * @param key key of the context variable to get
     * @return context variable specified by key, or null if not found
     */
    public abstract Object get(String key);

    /**
     * Add a context variable to MDC. If an existing value with the same key
     * exists, it is overridden.
     *
     * @param key key of the context variable
     * @param value value of the context variable
     */
    public abstract void put(String key, Object value);

    /**
     * Remove a context variable from MDC. If the value specified by the key
     * does not exist, this method does nothing.
     *
     * @param key key of the context variable to remove
     */
    public abstract void remove(String key);

}
