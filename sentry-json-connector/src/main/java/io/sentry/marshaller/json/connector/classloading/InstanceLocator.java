package io.sentry.marshaller.json.connector.classloading;

/**
 * Classes implementing this interface must locate classes in runtime classpath.
 * @param <T> Supertype of class to locate.
 */
public interface InstanceLocator<T> {
    /**
     * Gets located instance of T class.
     * @return Located instance in classpath.
     */
    T getInstance();
}
