package io.sentry.event.interfaces;

import java.io.Serializable;

/**
 * A SentryInterface is an additional structured data that can be provided with a message.
 */
public interface SentryInterface extends Serializable {
    /**
     * Gets the unique name of the interface.
     *
     * @return name of the interface.
     */
    String getInterfaceName();
}
