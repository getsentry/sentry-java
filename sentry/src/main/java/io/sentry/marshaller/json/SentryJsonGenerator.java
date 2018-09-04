package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.*;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

/**
 * JsonGenerator that makes an attempt at serializing any Java POJO to the "best"
 * JSON representation. For example, iterables should become JSON arrays, Maps should
 * become JSON objects, etc. As a fallback we use {@link Object#toString()}.
 *
 * Every method except {@link JsonGenerator#writeObject(Object)} is proxied to an
 * underlying {@link JsonGenerator}.
 */
public class SentryJsonGenerator extends JsonGenerator {
    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    private static final String RECURSION_LIMIT_HIT = "<recursion limit hit>";
    private static final int MAX_LENGTH_LIST = 10;
    private static final int MAX_SIZE_MAP = 50;
    private static final int MAX_LENGTH_STRING = 400;
    private static final int MAX_NESTING = 3;
    private static final String ELIDED = "...";

    private int maxLengthList;
    private int maxLengthString;
    private int maxSizeMap;
    private int maxNesting;
    private JsonGenerator generator;

    /**
     * Construct a JsonObjectMarshaller with the default configuration.
     *
     * @param generator Underlying JsonGenerator used for actual writing
     */
    public SentryJsonGenerator(JsonGenerator generator) {
        this.generator = generator;

        this.maxLengthList = MAX_LENGTH_LIST;
        this.maxLengthString = MAX_LENGTH_STRING;
        this.maxSizeMap = MAX_SIZE_MAP;
        this.maxNesting = MAX_NESTING;
    }

    /**
     * Serialize any object to JSON. Large collections are elided.
     *
     * @param value Value to write out.
     * @throws IOException On Jackson error (unserializable object).
     */
    public void writeObject(Object value) throws IOException {
        writeObject(value, 0);
    }

    private void writeObject(Object value, int recursionLevel) throws IOException {
        if (recursionLevel >= maxNesting) {
            generator.writeString(RECURSION_LIMIT_HIT);
            return;
        }

        if (value == null) {
            generator.writeNull();
        } else if (value.getClass().isArray()) {
            generator.writeStartArray();
            writeArray(value, recursionLevel);
            generator.writeEndArray();
        } else if (value instanceof Map) {
            generator.writeStartObject();
            int i = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (i >= maxSizeMap) {
                    break;
                }

                if (entry.getKey() == null) {
                    generator.writeFieldName("null");
                } else {
                    generator.writeFieldName(Util.trimString(entry.getKey().toString(), maxLengthString));
                }
                writeObject(entry.getValue(), recursionLevel + 1);

                i++;
            }
            generator.writeEndObject();
        } else if (value instanceof Collection) {
            generator.writeStartArray();
            int i = 0;
            for (Object subValue : (Collection<?>) value) {
                if (i >= maxLengthList) {
                    writeElided();
                    break;
                }

                writeObject(subValue, recursionLevel + 1);

                i++;
            }
            generator.writeEndArray();
        } else if (value instanceof String) {
            generator.writeString(Util.trimString((String) value, maxLengthString));
        } else {
            try {
                /** @see com.fasterxml.jackson.core.JsonGenerator#_writeSimpleObject(Object)  */
                generator.writeObject(value);
            } catch (IllegalStateException e) {
                logger.debug("Couldn't marshal '{}' of type '{}', had to be converted into a String",
                        value, value.getClass());
                try {
                    generator.writeString(Util.trimString(value.toString(), maxLengthString));
                } catch (Exception innerE) {
                    generator.writeString("<exception calling toString on object>");
                }
            }
        }
    }

    private void writeArray(Object value,
                            int recursionLevel) throws IOException {
        if (value instanceof byte[]) {
            byte[] byteArray = (byte[]) value;
            for (int i = 0; i < byteArray.length && i < maxLengthList; i++) {
                generator.writeNumber((int) byteArray[i]);
            }
            if (byteArray.length > maxLengthList) {
                writeElided();
            }
        } else if (value instanceof short[]) {
            short[] shortArray = (short[]) value;
            for (int i = 0; i < shortArray.length && i < maxLengthList; i++) {
                generator.writeNumber((int) shortArray[i]);
            }
            if (shortArray.length > maxLengthList) {
                writeElided();
            }
        } else if (value instanceof int[]) {
            int[] intArray = (int[]) value;
            for (int i = 0; i < intArray.length && i < maxLengthList; i++) {
                generator.writeNumber(intArray[i]);
            }
            if (intArray.length > maxLengthList) {
                writeElided();
            }
        } else if (value instanceof long[]) {
            long[] longArray = (long[]) value;
            for (int i = 0; i < longArray.length && i < maxLengthList; i++) {
                generator.writeNumber(longArray[i]);
            }
            if (longArray.length > maxLengthList) {
                writeElided();
            }
        } else if (value instanceof float[]) {
            float[] floatArray = (float[]) value;
            for (int i = 0; i < floatArray.length && i < maxLengthList; i++) {
                generator.writeNumber(floatArray[i]);
            }
            if (floatArray.length > maxLengthList) {
                writeElided();
            }
        } else if (value instanceof double[]) {
            double[] doubleArray = (double[]) value;
            for (int i = 0; i < doubleArray.length && i < maxLengthList; i++) {
                generator.writeNumber(doubleArray[i]);
            }
            if (doubleArray.length > maxLengthList) {
                writeElided();
            }
        } else if (value instanceof char[]) {
            char[] charArray = (char[]) value;
            for (int i = 0; i < charArray.length && i < maxLengthList; i++) {
                generator.writeString(String.valueOf(charArray[i]));
            }
            if (charArray.length > maxLengthList) {
                writeElided();
            }
        } else if (value instanceof boolean[]) {
            boolean[] boolArray = (boolean[]) value;
            for (int i = 0; i < boolArray.length && i < maxLengthList; i++) {
                generator.writeBoolean(boolArray[i]);
            }
            if (boolArray.length > maxLengthList) {
                writeElided();
            }
        } else {
            Object[] objArray = (Object[]) value;
            for (int i = 0; i < objArray.length && i < maxLengthList; i++) {
                writeObject(objArray[i], recursionLevel + 1);
            }
            if (objArray.length > maxLengthList) {
                writeElided();
            }
        }
    }

    private void writeElided() throws IOException {
        generator.writeString(ELIDED);
    }

    public void setMaxLengthList(int maxLengthList) {
        this.maxLengthList = maxLengthList;
    }

    public void setMaxLengthString(int maxLengthString) {
        this.maxLengthString = maxLengthString;
    }

    public void setMaxSizeMap(int maxSizeMap) {
        this.maxSizeMap = maxSizeMap;
    }

    public void setMaxNesting(int maxNesting) {
        this.maxNesting = maxNesting;
    }

    @Override
    public JsonGenerator setCodec(ObjectCodec oc) {
        return generator.setCodec(oc);
    }

    @Override
    public ObjectCodec getCodec() {
        return generator.getCodec();
    }

    @Override
    public Version version() {
        return generator.version();
    }

    @Override
    public JsonGenerator enable(Feature f) {
        return generator.enable(f);
    }

    @Override
    public JsonGenerator disable(Feature f) {
        return generator.disable(f);
    }

    @Override
    public boolean isEnabled(Feature f) {
        return generator.isEnabled(f);
    }

    @Override
    public int getFeatureMask() {
        return generator.getFeatureMask();
    }

    @Override
    public JsonGenerator setFeatureMask(int values) {
        return generator.setFeatureMask(values);
    }

    @Override
    public JsonGenerator useDefaultPrettyPrinter() {
        return generator.useDefaultPrettyPrinter();
    }

    @Override
    public void writeStartArray() throws IOException {
        generator.writeStartArray();
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
    public void writeFieldName(SerializableString name) throws IOException {
        generator.writeFieldName(name);
    }

    @Override
    public void writeString(String text) throws IOException {
        generator.writeString(text);
    }

    @Override
    public void writeString(char[] text, int offset, int len) throws IOException {
        generator.writeString(text, offset, len);
    }

    @Override
    public void writeString(SerializableString text) throws IOException {
        generator.writeString(text);
    }

    @Override
    public void writeRawUTF8String(byte[] text, int offset, int length) throws IOException {
        generator.writeRawUTF8String(text, offset, length);
    }

    @Override
    public void writeUTF8String(byte[] text, int offset, int length) throws IOException {
        generator.writeUTF8String(text, offset, length);
    }

    @Override
    public void writeRaw(String text) throws IOException {
        generator.writeRaw(text);
    }

    @Override
    public void writeRaw(String text, int offset, int len) throws IOException {
        generator.writeRaw(text, offset, len);
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) throws IOException {
        generator.writeRaw(text, offset, len);
    }

    @Override
    public void writeRaw(char c) throws IOException {
        generator.writeRaw(c);
    }

    @Override
    public void writeRawValue(String text) throws IOException {
        generator.writeRawValue(text);
    }

    @Override
    public void writeRawValue(String text, int offset, int len) throws IOException {
        generator.writeRawValue(text, offset, len);
    }

    @Override
    public void writeRawValue(char[] text, int offset, int len) throws IOException {
        generator.writeRawValue(text, offset, len);
    }

    @Override
    public void writeBinary(Base64Variant bv, byte[] data, int offset, int len) throws IOException {
        generator.writeBinary(bv, data, offset, len);
    }

    @Override
    public int writeBinary(Base64Variant bv, InputStream data, int dataLength) throws IOException {
        return generator.writeBinary(bv, data, dataLength);
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
    public void writeTree(TreeNode rootNode) throws IOException {
        generator.writeTree(rootNode);
    }

    @Override
    public JsonStreamContext getOutputContext() {
        return generator.getOutputContext();
    }

    @Override
    public void flush() throws IOException {
        generator.flush();
    }

    @Override
    public boolean isClosed() {
        return generator.isClosed();
    }

    @Override
    public void close() throws IOException {
        generator.close();
    }
}
