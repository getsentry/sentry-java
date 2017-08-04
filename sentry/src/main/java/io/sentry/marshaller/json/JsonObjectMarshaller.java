package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
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
    private static final int MAX_LENGTH_STRING = 400;
    private static final String ELIDED = "...";

    /**
     * Construct a JsonObjectMarshaller with the default configuration.
     */
    public JsonObjectMarshaller() {

    }

    /**
     * Serialize almost any object to JSON.
     *
     * @param generator JsonGenerator to write object out to.
     * @param value Value to write out.
     * @throws IOException On Jackson error (unserializable object).
     */
    public void writeObject(JsonGenerator generator, Object value) throws IOException {
        boolean isPrimitiveArray = getIsPrimitiveArray(value);

        // TODO: handle max recursion
        // TODO: handle cycles
        // default frame allowance of 25
        // default 4k bytes of vars per frame, after that they are silently dropped

        if (value == null) {
            generator.writeNull();
        } else if (isPrimitiveArray) {
            generator.writeStartArray();
            writePrimitiveArray(generator, value);
            generator.writeEndArray();
        } else if (value.getClass().isArray()) {
            // Object arrays

        } else if (value instanceof Path) {
            // Path is weird because it implements Iterable, and then the iterator returns
            // more Paths, which are iterable... which would cause a stack overflow below.
            generator.writeString(value.toString());
        } else if (value instanceof Iterable) {
            // TODO: elide long iterables
            generator.writeStartArray();
            for (Object subValue : (Iterable<?>) value) {
                writeObject(generator, subValue);
            }
            generator.writeEndArray();
        } else if (value instanceof Map) {
            // TODO: elide large maps
            generator.writeStartObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() == null) {
                    generator.writeFieldName("null");
                } else {
                    generator.writeFieldName(entry.getKey().toString());
                }
                writeObject(generator, entry.getValue());
            }
            generator.writeEndObject();
        } else {
            try {
                /** @see com.fasterxml.jackson.core.JsonGenerator#_writeSimpleObject(Object)  */
                generator.writeObject(value);
            } catch (IllegalStateException e) {
                logger.debug("Couldn't marshal '{}' of type '{}', had to be converted into a String",
                    value, value.getClass());
                generator.writeString(value.toString());
            }
        }
    }

    private boolean getIsPrimitiveArray(Object value) {
        if (value == null) {
            return false;
        } else if (!value.getClass().isArray()) {
            return false;
        } else if (value instanceof byte[]) {
            return true;
        } else if (value instanceof short[]) {
            return true;
        } else if (value instanceof int[]) {
            return true;
        } else if (value instanceof long[]) {
            return true;
        } else if (value instanceof float[]) {
            return true;
        } else if (value instanceof double[]) {
            return true;
        } else if (value instanceof char[]) {
            return true;
        } else if (value instanceof boolean[]) {
            return true;
        } else {
            return false;
        }
    }

    private void writePrimitiveArray(JsonGenerator generator, Object value) throws IOException {
        if (value instanceof byte[]) {
            byte[] castArray = (byte[]) value;
            for (int i = 0; i < castArray.length && i <= MAX_LENGTH_LIST; i++) {
                // TODO: how *should* we serialize bytes?
                generator.writeNumber((int) castArray[i]);
            }
            if (castArray.length > MAX_LENGTH_LIST) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof short[]) {
            short[] castArray = (short[]) value;
            for (int i = 0; i < castArray.length && i <= MAX_LENGTH_LIST; i++) {
                generator.writeNumber((int) castArray[i]);
            }
            if (castArray.length > MAX_LENGTH_LIST) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof int[]) {
            int[] castArray = (int[]) value;
            for (int i = 0; i < castArray.length && i <= MAX_LENGTH_LIST; i++) {
                generator.writeNumber(castArray[i]);
            }
            if (castArray.length > MAX_LENGTH_LIST) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof long[]) {
            long[] castArray = (long[]) value;
            for (int i = 0; i < castArray.length && i <= MAX_LENGTH_LIST; i++) {
                generator.writeNumber(castArray[i]);
            }
            if (castArray.length > MAX_LENGTH_LIST) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof float[]) {
            float[] castArray = (float[]) value;
            for (int i = 0; i < castArray.length && i <= MAX_LENGTH_LIST; i++) {
                generator.writeNumber(castArray[i]);
            }
            if (castArray.length > MAX_LENGTH_LIST) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof double[]) {
            double[] castArray = (double[]) value;
            for (int i = 0; i < castArray.length && i <= MAX_LENGTH_LIST; i++) {
                generator.writeNumber(castArray[i]);
            }
            if (castArray.length > MAX_LENGTH_LIST) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof char[]) {
            char[] castArray = (char[]) value;
            for (int i = 0; i < castArray.length && i <= MAX_LENGTH_LIST; i++) {
                generator.writeString(String.valueOf(castArray[i]));
            }
            if (castArray.length > MAX_LENGTH_LIST) {
                generator.writeString(ELIDED);
            }
        } else if (value instanceof boolean[]) {
            boolean[] castArray = (boolean[]) value;
            for (int i = 0; i < castArray.length && i <= MAX_LENGTH_LIST; i++) {
                generator.writeBoolean(castArray[i]);
            }
            if (castArray.length > MAX_LENGTH_LIST) {
                generator.writeString(ELIDED);
            }
        }
    }
}
