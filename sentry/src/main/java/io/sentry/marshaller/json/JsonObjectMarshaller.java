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
 *
 * Not thread safe because it has to maintain a lot of state. Construct a new
 * JsonObjectMarshaller for each group of objects you want to marshall from a
 * single thread.
 */
public class JsonObjectMarshaller {
    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    private static final int MAX_LENGTH_LIST = 50;
    private static final int MAX_SIZE_MAP = 50;
    private static final int MAX_LENGTH_STRING = 400;
    private static final String ELIDED = "...";

    private int maxLengthList;
    private int maxLengthString;
    private int maxSizeMap;

    /**
     * Construct a JsonObjectMarshaller with the default configuration.
     */
    public JsonObjectMarshaller() {
        this.maxLengthList = MAX_LENGTH_LIST;
        maxSizeMap = MAX_SIZE_MAP;
        this.maxLengthString = MAX_LENGTH_STRING;
    }

    /**
     * Serialize almost any object to JSON.
     *
     * @param generator JsonGenerator to write object out to.
     * @param value Value to write out.
     * @throws IOException On Jackson error (unserializable object).
     */
    public void writeObject(JsonGenerator generator, Object value) throws IOException {
        // TODO: handle max recursion/nesting
        // TODO: handle cycles
        // TODO: from python: default frame allowance of 25
        // TODO: from python: default 4k bytes of vars per frame, after that they are silently dropped

        if (value == null) {
            generator.writeNull();
        } else if (value.getClass().isArray()) {
            generator.writeStartArray();
            writeArray(generator, value);
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
                    generator.writeString(ELIDED);
                    break;
                }
                writeObject(generator, subValue);
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
                    generator.writeFieldName(entry.getKey().toString());
                }
                writeObject(generator, entry.getValue());
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

    private void writeArray(JsonGenerator generator, Object value) throws IOException {
        if (value instanceof byte[]) {
            byte[] castArray = (byte[]) value;
            for (int i = 0; i < castArray.length && i < maxLengthList; i++) {
                // TODO: how *should* we serialize bytes?
                generator.writeNumber((int) castArray[i]);
            }
            if (castArray.length > maxLengthList) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof short[]) {
            short[] castArray = (short[]) value;
            for (int i = 0; i < castArray.length && i < maxLengthList; i++) {
                generator.writeNumber((int) castArray[i]);
            }
            if (castArray.length > maxLengthList) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof int[]) {
            int[] castArray = (int[]) value;
            for (int i = 0; i < castArray.length && i < maxLengthList; i++) {
                generator.writeNumber(castArray[i]);
            }
            if (castArray.length > maxLengthList) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof long[]) {
            long[] castArray = (long[]) value;
            for (int i = 0; i < castArray.length && i < maxLengthList; i++) {
                generator.writeNumber(castArray[i]);
            }
            if (castArray.length > maxLengthList) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof float[]) {
            float[] castArray = (float[]) value;
            for (int i = 0; i < castArray.length && i < maxLengthList; i++) {
                generator.writeNumber(castArray[i]);
            }
            if (castArray.length > maxLengthList) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof double[]) {
            double[] castArray = (double[]) value;
            for (int i = 0; i < castArray.length && i < maxLengthList; i++) {
                generator.writeNumber(castArray[i]);
            }
            if (castArray.length > maxLengthList) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof char[]) {
            char[] castArray = (char[]) value;
            for (int i = 0; i < castArray.length && i < maxLengthList; i++) {
                generator.writeString(String.valueOf(castArray[i]));
            }
            if (castArray.length > maxLengthList) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof boolean[]) {
            boolean[] castArray = (boolean[]) value;
            for (int i = 0; i < castArray.length && i < maxLengthList; i++) {
                generator.writeBoolean(castArray[i]);
            }
            if (castArray.length > maxLengthList) {
                generator.writeString(ELIDED);
            }
        } else {
            // must be an Object[]
            Object[] castArray = (Object[]) value;
            for (int i = 0; i < castArray.length && i < maxLengthList; i++) {
                writeObject(generator, castArray[i]);
            }
            if (castArray.length > maxLengthList) {
                generator.writeString(ELIDED);
            }
        }
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

}
