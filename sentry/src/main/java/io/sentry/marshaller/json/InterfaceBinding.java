package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.event.interfaces.SentryInterface;

import java.io.IOException;

/**
 * An interface binding allows to encore a {@link SentryInterface} of a specific type into a JSON stream.
 *
 * @param <T> type of {@link SentryInterface} supported by the binder.
 */
public interface InterfaceBinding<T extends SentryInterface> {
    /**
     * Encodes the content of a sentry interface into a JSON stream.
     *
     * @param generator       JSON generator allowing to write JSON content.
     * @param sentryInterface interface to encode.
     * @throws IOException thrown in case of failure during the generation of JSON content.
     */
    void writeInterface(JsonGenerator generator, T sentryInterface) throws IOException;
}
