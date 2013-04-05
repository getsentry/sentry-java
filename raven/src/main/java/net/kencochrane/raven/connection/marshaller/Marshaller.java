package net.kencochrane.raven.connection.marshaller;

import net.kencochrane.raven.event.LoggedEvent;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Marshaller allows to serialise a {@link LoggedEvent} and sends over a stream.
 */
public interface Marshaller {
    /**
     * Serialise an event and sends it through an {@code OutputStream}.
     *
     * @param event       event to serialise.
     * @param destination destination stream.
     * @throws IOException occurs when the serialisation failed.
     */
    void marshall(LoggedEvent event, OutputStream destination) throws IOException;
}
