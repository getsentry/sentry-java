package net.kencochrane.raven.event.interfaces;

/**
 * A SentryInterface is an additional structured data that can be provided with a message.
 */
public interface SentryInterface {
    /**
     * Gets the unique name of the interface.
     *
     * @return name of the interface.
     */
    String getInterfaceName();
}
