package io.sentry.marshaller.json.connector;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Json Facade to any Json Writer implementation.
 */
public interface JsonGenerator extends AutoCloseable {

    /**
     * Start property with field name.
     * @param fieldName Name of the json property key.
     * @throws IOException when there's and issue with output stream.
     */
    void writeFieldName(String fieldName) throws IOException;

    /**
     * Start a json object block.
     * @throws IOException when there's and issue with output stream.
     */
    void writeStartObject() throws IOException;

    /**
     * Start json object property block.
     * @param fieldName Name of the json property key.
     * @throws IOException when there's and issue with output stream.
     */
    void writeObjectFieldStart(String fieldName) throws IOException;

    /**
     * Set Object value to Json.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeObject(Object value) throws IOException;

    /**
     * Add object property to Json.
     * @param fieldName Name of the json property key.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeObjectField(String fieldName, Object value) throws IOException;

    /**
     * Ends a Json object block.
     * @throws IOException when there's and issue with output stream.
     */
    void writeEndObject() throws IOException;

    /**
     * Start Json array block.
     * @throws IOException when there's and issue with output stream.
     */
    void writeStartArray() throws IOException;

    /**
     * Start Array property Json.
     * @param fieldName Name of the json property key.
     * @throws IOException when there's and issue with output stream.
     */
    void writeArrayFieldStart(String fieldName) throws IOException;

    /**
     * Ends a Json array block.
     * @throws IOException when there's and issue with output stream.
     */
    void writeEndArray() throws IOException;

    /**
     * Add String property to Json.
     * @param fieldName Name of the json property key.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeStringField(String fieldName, String value) throws IOException;

    /**
     * Set String value to Json.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeString(String value) throws IOException;

    /**
     * Set boolean value to Json.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeBoolean(boolean value) throws IOException;

    /**
     * Add boolean property to Json.
     * @param fieldName Name of the json property key.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeBooleanField(String fieldName, boolean value) throws IOException;

    /**
     * Set Encoded Number value to Json.
     * @param encodedValue Encoded Number value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumber(String encodedValue) throws IOException;

    /**
     * Set BidDecimal number value to Json.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumber(BigDecimal value) throws IOException;

    /**
     * Set float value to Json.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumber(float value) throws IOException;

    /**
     * Set double value to Json.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumber(double value) throws IOException;

    /**
     * Set long value to Json.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumber(long value) throws IOException;

    /**
     * Set int value to Json.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumber(int value) throws IOException;

    /**
     * Set BitInteger value to Json.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumber(BigInteger value) throws IOException;

    /**
     * Add BigDecimal property to Json.
     * @param fieldName Name of the json property key.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumberField(String fieldName, BigDecimal value) throws IOException;

    /**
     * Add float property to Json.
     * @param fieldName Name of the json property key.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumberField(String fieldName, float value) throws IOException;

    /**
     * Add double property to Json.
     * @param fieldName Name of the json property key.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumberField(String fieldName, double value) throws IOException;

    /**
     * Add long property to Json.
     * @param fieldName Name of the json property key.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumberField(String fieldName, long value) throws IOException;

    /**
     * Add int property to Json.
     * @param fieldName Name of the json property key.
     * @param value Value to set in json property.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNumberField(String fieldName, int value) throws IOException;


    /**
     * Set Null value to Json.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNull() throws IOException;

    /**
     * Add Null property to Json.
     * @param fieldName Name of the json property key.
     * @throws IOException when there's and issue with output stream.
     */
    void writeNullField(String fieldName) throws IOException;

    /**
     * Closes generator output stream.
     * @throws IOException when issues with OutputStream.
     */
    void close() throws IOException;
}
