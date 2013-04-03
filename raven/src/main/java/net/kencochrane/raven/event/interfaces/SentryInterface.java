package net.kencochrane.raven.event.interfaces;

import java.util.Map;

public interface SentryInterface {
    /**
     * Gets the unique name of the interface.
     *
     * @return name of the interface.
     */
    String getInterfaceName();

    /**
     * Gets the content of the interface as a Map.
     * <p>
     * The values contained in the Map can only be:
     * <ul>
     * <li>A {@code String}</li>
     * <li>A wrapped primitive</li>
     * <li>A {@code Collection<String>}</li>
     * <li>A {@code Map<String, V>} where {@code V} is one of the types currently listed</li>
     * </ul>
     * </p>
     *
     * @return the content of the interface.
     */
    Map<String, Object> getInterfaceContent();
}
