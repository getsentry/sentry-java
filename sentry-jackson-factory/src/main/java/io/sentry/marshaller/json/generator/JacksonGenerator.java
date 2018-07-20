package io.sentry.marshaller.json.generator;

import io.sentry.marshaller.json.connector.JsonGenerator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Jackson Json Generator Facade, it just delegates directly to <code>com.fasterxml.jackson.core.JsonGenerator</code>.
 */
public class JacksonGenerator implements JsonGenerator {
    private final com.fasterxml.jackson.core.JsonGenerator generator;

    /**
     * Creates a new facade delegating to parameter  generator.
     *
     * @param generator Jackson Core JsonGenerator instance.
     */
    public JacksonGenerator(com.fasterxml.jackson.core.JsonGenerator generator) {
        this.generator = generator;
    }

    @Override
    public void writeObject(Object value) throws IOException {
        generator.writeObject(value);
    }

    @Override
    public void writeStartArray() throws IOException {
        generator.writeStartArray();
    }

    @Override
    public void writeStringField(String fieldName, String value) throws IOException {
        generator.writeStringField(fieldName, value);
    }

    @Override
    public void writeArrayFieldStart(String fieldName) throws IOException {
        generator.writeArrayFieldStart(fieldName);
    }

    @Override
    public void writeBooleanField(String fieldName, boolean value) throws IOException {
        generator.writeBooleanField(fieldName, value);
    }

    @Override
    public void writeObjectFieldStart(String fieldName) throws IOException {
        generator.writeObjectFieldStart(fieldName);
    }

    @Override
    public void writeObjectField(String fieldName, Object value) throws IOException {
        generator.writeObjectField(fieldName, value);
    }

    @Override
    public void writeNullField(String fieldName) throws IOException {
        generator.writeNullField(fieldName);
    }

    @Override
    public void writeEndArray() throws IOException {
        generator.writeEndArray();
    }

    @Override
    public void writeStartObject() throws IOException {
        generator.writeStartObject();
    }

    @Override
    public void writeEndObject() throws IOException {
        generator.writeEndObject();
    }

    @Override
    public void writeFieldName(String name) throws IOException {
        generator.writeFieldName(name);
    }

    @Override
    public void writeString(String text) throws IOException {
        generator.writeString(text);
    }

    @Override
    public void writeNumber(int v) throws IOException {
        generator.writeNumber(v);
    }

    @Override
    public void writeNumber(long v) throws IOException {
        generator.writeNumber(v);
    }

    @Override
    public void writeNumber(BigInteger v) throws IOException {
        generator.writeNumber(v);
    }

    @Override
    public void writeNumber(double v) throws IOException {
        generator.writeNumber(v);
    }

    @Override
    public void writeNumber(float v) throws IOException {
        generator.writeNumber(v);
    }

    @Override
    public void writeNumber(BigDecimal v) throws IOException {
        generator.writeNumber(v);
    }

    @Override
    public void writeNumber(String encodedValue) throws IOException {
        generator.writeNumber(encodedValue);
    }

    @Override
    public void writeBoolean(boolean state) throws IOException {
        generator.writeBoolean(state);
    }

    @Override
    public void writeNull() throws IOException {
        generator.writeNull();
    }

    @Override
    public void writeNumberField(String fieldName, BigDecimal value) throws IOException {
        generator.writeNumberField(fieldName, value);
    }

    @Override
    public void writeNumberField(String fieldName, float value) throws IOException {
        generator.writeNumberField(fieldName, value);
    }

    @Override
    public void writeNumberField(String fieldName, double value) throws IOException {
        generator.writeNumberField(fieldName, value);
    }

    @Override
    public void writeNumberField(String fieldName, long value) throws IOException {
        generator.writeNumberField(fieldName, value);
    }

    @Override
    public void writeNumberField(String fieldName, int value) throws IOException {
        generator.writeNumberField(fieldName, value);
    }

    @Override
    public void close() throws IOException {
        generator.close();
    }
}
