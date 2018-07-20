package io.sentry.marshaller.json.factory;

import io.sentry.marshaller.json.connector.JsonFactory;
import io.sentry.marshaller.json.connector.JsonGenerator;
import io.sentry.marshaller.json.generator.GsonGenerator;

import java.io.IOException;
import java.io.OutputStream;

/**
 * JsonFactory implementation for runtime Gson Injection.
 */
public class JsonFactoryImpl implements JsonFactory {

    @Override
    public JsonGenerator createGenerator(OutputStream destination) throws IOException {
        return new GsonGenerator(destination);
    }
}
