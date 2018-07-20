package io.sentry.marshaller.json.factory;

import io.sentry.marshaller.json.connector.JsonFactory;
import io.sentry.marshaller.json.connector.JsonGenerator;
import io.sentry.marshaller.json.generator.JacksonGenerator;

import java.io.IOException;
import java.io.OutputStream;

/**
 * JsonFactory implementation for runtime Jackson Injection.
 */
public class JsonFactoryImpl implements JsonFactory {
    private final com.fasterxml.jackson.core.JsonFactory jsonFactory = new com.fasterxml.jackson.core.JsonFactory();

    @Override
    public JsonGenerator createGenerator(OutputStream destination) throws IOException {
        return wrap(jsonFactory.createGenerator(destination));
    }

    private JsonGenerator wrap(com.fasterxml.jackson.core.JsonGenerator generator) {
        return new JacksonGenerator(generator);
    }
}
