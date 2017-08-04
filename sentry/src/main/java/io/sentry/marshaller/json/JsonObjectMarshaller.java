package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Marshaller that makes an attempt at serializing any Java POJO to the "best"
 * JSON representation. For example, iterables should become JSON arrays, Maps should
 * become JSON objects, etc. As a fallback we use {@link Object#toString()}.
 */
public class JsonObjectMarshaller {
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

    /**
     * Construct a JsonObjectMarshaller with the default configuration.
     */
    public JsonObjectMarshaller() {
        this.maxLengthList = MAX_LENGTH_LIST;
        this.maxLengthString = MAX_LENGTH_STRING;
        this.maxSizeMap = MAX_SIZE_MAP;
        this.maxNesting = MAX_NESTING;
    }

    /**
     * Serialize any object to JSON. Large collections are elided.
     *
     * @param generator JsonGenerator to write object out to.
     * @param value Value to write out.
     * @throws IOException On Jackson error (unserializable object).
     */
    public void writeObject(JsonGenerator generator, Object value) throws IOException {
        writeObject(generator, value, 0);
    }

    private void writeObject(JsonGenerator generator, Object value, int recursionLevel) throws IOException {
        if (recursionLevel >= maxNesting) {
            generator.writeString(RECURSION_LIMIT_HIT);
            return;
        }

        if (value == null) {
            generator.writeNull();
        } else if (value.getClass().isArray()) {
            generator.writeStartArray();
            writeArray(generator, value, recursionLevel);
            generator.writeEndArray();
        } else if (value instanceof Path) {
            // Path is weird because it implements Iterable, and then the iterator returns
            // more Paths, which are iterable... which would cause a stack overflow below.
            generator.writeString(Util.trimString(value.toString(), maxLengthString));
        } else if (value instanceof Iterable) {
            generator.writeStartArray();
            int i = 0;
            for (Object subValue : (Iterable<?>) value) {
                if (i >= maxLengthList) {
                    writeElided(generator);
                    break;
                }

                writeObject(generator, subValue, recursionLevel + 1);

                i++;
            }
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
                writeObject(generator, entry.getValue(), recursionLevel + 1);

                i++;
            }
            generator.writeEndObject();
        } else if (value instanceof String) {
            generator.writeString(Util.trimString((String) value, maxLengthString));
        } else {
            try {
                /** @see com.fasterxml.jackson.core.JsonGenerator#_writeSimpleObject(Object)  */
                generator.writeObject(value);
            } catch (IllegalStateException e) {
                logger.debug("Couldn't marshal '{}' of type '{}', had to be converted into a String",
                    value, value.getClass());
                generator.writeString(Util.trimString(value.toString(), maxLengthString));
            }
        }
    }

    private void writeArray(JsonGenerator generator,
                            Object value,
                            int recursionLevel) throws IOException {
        if (value instanceof byte[]) {
            byte[] byteArray = (byte[]) value;
            for (int i = 0; i < byteArray.length && i < maxLengthList; i++) {
                generator.writeNumber((int) byteArray[i]);
            }
            if (byteArray.length > maxLengthList) {
                writeElided(generator);
            }
        } else if (value instanceof short[]) {
            short[] shortArray = (short[]) value;
            for (int i = 0; i < shortArray.length && i < maxLengthList; i++) {
                generator.writeNumber((int) shortArray[i]);
            }
            if (shortArray.length > maxLengthList) {
                writeElided(generator);
            }
        } else if (value instanceof int[]) {
            int[] intArray = (int[]) value;
            for (int i = 0; i < intArray.length && i < maxLengthList; i++) {
                generator.writeNumber(intArray[i]);
            }
            if (intArray.length > maxLengthList) {
                writeElided(generator);
            }
        } else if (value instanceof long[]) {
            long[] longArray = (long[]) value;
            for (int i = 0; i < longArray.length && i < maxLengthList; i++) {
                generator.writeNumber(longArray[i]);
            }
            if (longArray.length > maxLengthList) {
                writeElided(generator);
            }
        } else if (value instanceof float[]) {
            float[] floatArray = (float[]) value;
            for (int i = 0; i < floatArray.length && i < maxLengthList; i++) {
                generator.writeNumber(floatArray[i]);
            }
            if (floatArray.length > maxLengthList) {
                writeElided(generator);
            }
        } else if (value instanceof double[]) {
            double[] doubleArray = (double[]) value;
            for (int i = 0; i < doubleArray.length && i < maxLengthList; i++) {
                generator.writeNumber(doubleArray[i]);
            }
            if (doubleArray.length > maxLengthList) {
                writeElided(generator);
            }
        } else if (value instanceof char[]) {
            char[] charArray = (char[]) value;
            for (int i = 0; i < charArray.length && i < maxLengthList; i++) {
                generator.writeString(String.valueOf(charArray[i]));
            }
            if (charArray.length > maxLengthList) {
                writeElided(generator);
            }
        } else if (value instanceof boolean[]) {
            boolean[] boolArray = (boolean[]) value;
            for (int i = 0; i < boolArray.length && i < maxLengthList; i++) {
                generator.writeBoolean(boolArray[i]);
            }
            if (boolArray.length > maxLengthList) {
                writeElided(generator);
            }
        } else {
            Object[] objArray = (Object[]) value;
            for (int i = 0; i < objArray.length && i < maxLengthList; i++) {
                writeObject(generator, objArray[i], recursionLevel + 1);
            }
            if (objArray.length > maxLengthList) {
                writeElided(generator);
            }
        }
    }

    private void writeElided(JsonGenerator generator) throws IOException {
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

}
