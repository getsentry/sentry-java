package net.kencochrane.raven.connection.marshaller;

import net.kencochrane.raven.event.LoggedEvent;

import java.io.OutputStream;

/**
 * Marshaller allows to serialise a {@link LoggedEvent} and sends over a stream.
 */
public interface Marshaller {
    /**
     * Serialise an event and sends it through an {@code OutputStream}.
     * <p>
     * The marshaller will close the given stream once it's done sending content.
     * If it should stay open, use a wrapper that will intercept the call to {@code OutputStream#close()}.
     * </p>
     *
     * @param event       event to serialise.
     * @param destination destination stream.
     */
    void marshall(LoggedEvent event, OutputStream destination);
}
