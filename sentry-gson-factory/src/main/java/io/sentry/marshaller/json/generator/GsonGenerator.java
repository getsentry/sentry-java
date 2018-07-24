package io.sentry.marshaller.json.generator;

import com.google.gson.stream.JsonWriter;
import io.sentry.marshaller.json.connector.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gson Json Generator Facade, it just delegates directly to <code>com.fasterxml.jackson.core.JsonGenerator</code>.
 */
public class GsonGenerator implements JsonGenerator {
    private final JsonWriter writer;

    /**
     * Creates a new facade delegating to @{@code com.google.gson.stream.JsonWriter}.
     *
     * @param destination Destination OutputStream whose Json will be written.
     */
    public GsonGenerator(OutputStream destination) {
        this.writer = new JsonWriter(new OutputStreamWriter(destination));
    }

    @Override
    public void writeStartObject() throws IOException {
        writer.beginObject();
    }

    @Override
    public void writeStringField(String fieldName, String value) throws IOException {
        writer.name(fieldName).value(value);
    }

    @Override
    public void writeArrayFieldStart(String fieldName) throws IOException {
        writer.name(fieldName).beginArray();
    }

    @Override
    public void writeString(String value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeEndArray() throws IOException {
        writer.endArray();
    }

    @Override
    public void writeEndObject() throws IOException {
        writer.endObject();
    }

    @Override
    public void writeNull() throws IOException {
        writer.nullValue();
    }

    @Override
    public void writeStartArray() throws IOException {
        writer.beginArray();
    }

    @Override
    public void writeFieldName(String fieldName) throws IOException {
        writer.name(fieldName);
    }

    @Override
    public void writeObject(Object value) throws IOException {
        writeSimpleObject(value);
    }

    @Override
    public void writeObjectField(String fieldName, Object value) throws IOException {
        writer.name(fieldName);
        writeSimpleObject(value);
    }

    /**
     * Tries to write object value to simple types or error out.
     *
     * @param value any object value.
     * @throws IOException when there's and issue with output stream.
     */
    private void writeSimpleObject(Object value) throws IOException {
        if (value == null) {
            this.writeNull();
        } else if (value instanceof String) {
            this.writeString((String) value);
        } else {
            if (value instanceof Number) {
                Number n = (Number) value;
                this.writeNumber(n);
                return;
            } else {
                if (value instanceof byte[]) {
                    this.writeBinary((byte[]) value);
                    return;
                }

                if (value instanceof Boolean) {
                    this.writeBoolean((Boolean) value);
                    return;
                }

                if (value instanceof AtomicBoolean) {
                    this.writeBoolean(((AtomicBoolean) value).get());
                    return;
                }
            }

            throw new IllegalStateException(GsonGenerator.class.getSimpleName()
                    + " can only serialize simple wrapper types (type passed "
                    + value.getClass().getName()
                    + ")");
        }
    }

    /**
     * NOT SUPPORTED YET.
     * Writes byte array to json output as field value.
     *
     * @param bytes byte array object.
     */
    private void writeBinary(byte[] bytes) {
        throw new IllegalStateException(GsonGenerator.class.getSimpleName()
                + " don't support serializing byte[] yet.");
    }

    private void writeNumber(Number number) throws IOException {
        writer.value(number);
    }

    @Override
    public void writeBoolean(boolean state) throws IOException {
        writer.value(state);
    }

    @Override
    public void writeBooleanField(String fieldName, boolean value) throws IOException {
        writer.name(fieldName).value(value);
    }

    @Override
    public void writeObjectFieldStart(String fieldName) throws IOException {
        writer.name(fieldName).beginObject();
    }

    @Override
    public void writeNullField(String fieldName) throws IOException {
        writer.name(fieldName).nullValue();
    }

    @Override
    public void writeNumber(String encodedValue) throws IOException {
        try {
            writer.value(new BigInteger(encodedValue));
        } catch (Exception iError) {
            try {
                writer.value(new BigDecimal(encodedValue));
            } catch (Exception dError) {
                writer.value(encodedValue);
            }
        }

    }

    @Override
    public void writeNumber(BigDecimal value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeNumber(float value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeNumber(double value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeNumber(long value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeNumber(int value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeNumberField(String fieldName, BigDecimal value) throws IOException {
        writer.name(fieldName).value(value);
    }

    @Override
    public void writeNumberField(String fieldName, float value) throws IOException {
        writer.name(fieldName).value(value);
    }

    @Override
    public void writeNumberField(String fieldName, double value) throws IOException {
        writer.name(fieldName).value(value);
    }

    @Override
    public void writeNumberField(String fieldName, long value) throws IOException {
        writer.name(fieldName).value(value);
    }

    @Override
    public void writeNumberField(String fieldName, int value) throws IOException {
        writer.name(fieldName).value(value);
    }

    @Override
    public void writeNumber(BigInteger value) throws IOException {
        writer.value(value);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
