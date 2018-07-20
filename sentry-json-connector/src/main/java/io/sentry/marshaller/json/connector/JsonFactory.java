package io.sentry.marshaller.json.connector;

import java.io.IOException;
import java.io.OutputStream;

/**
 * JsonFactory allows to create Json Serializer from a json library dependency.
 */
public interface JsonFactory {
    /**
     * Creates a instance of <code>JsonGenerator</code> from the destination stream.
     * @param destination Json serialization <code>OutputStream</code>
     * @return A new instance of <code>JsonGenerator</code>
     * @throws IOException when there are issues handling destination stream.
     */
    JsonGenerator createGenerator(OutputStream destination) throws IOException;
}
