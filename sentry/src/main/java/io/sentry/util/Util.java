package io.sentry.util;

import com.fasterxml.jackson.core.JsonGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Sentry static Utility class.
 */
public final class Util {
    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    // Hide the constructor.
    private Util() {

    }

    /**
     * Returns {@code true} if the given string is null or is the empty string.
     *
     * @param string a string reference to check
     * @return {@code true} if the string is null or is the empty string
     */
    public static boolean isNullOrEmpty(String string) {
        return string == null || string.length() == 0; // string.isEmpty() in Java 6
    }

    private static Map<String, String> parseCsv(String inputString, String typeName) {
        if (isNullOrEmpty(inputString)) {
            return Collections.emptyMap();
        }

        String[] entries = inputString.split(",");
        Map<String, String> map = new LinkedHashMap<>();
        for (String entry : entries) {
            String[] split = entry.split(":");
            if (split.length != 2) {
                throw new IllegalArgumentException("Invalid " + typeName + " entry: " + entry);
            }
            map.put(split[0], split[1]);
        }
        return map;
    }

    /**
     * Parses the provided tags string into a Map of String -&gt; String.
     *
     * @param tagsString comma-delimited key-value pairs, e.g. "tag1:value1,tag2:value2".
     * @return Map of tags e.g. (tag1 -&gt; value1, tag2 -&gt; value2)
     */
    public static Map<String, String> parseTags(String tagsString) {
        return parseCsv(tagsString, "tags");
    }

    /**
     * Parses the provided extras string into a Map of String -&gt; String.
     *
     * @param extrasString comma-delimited key-value pairs, e.g. "extra1:value1,extra2:value2".
     * @return Map of extras e.g. (extra1 -&gt; value1, extra2 -&gt; value2)
     */
    public static Map<String, String> parseExtra(String extrasString) {
        return parseCsv(extrasString, "extras");
    }

    /**
     * Parses the provided extraTags string into a Set of Strings.
     *
     * @param extraTagsString comma-delimited tags
     * @return Set of Strings representing extra tags
     * @deprecated prefer {@link Util#parseMdcTags(String)}
     */
    @Deprecated
    public static Set<String> parseExtraTags(String extraTagsString) {
        return parseMdcTags(extraTagsString);
    }

    /**
     * Parses the provided Strings into a Set of Strings.
     *
     * @param mdcTagsString comma-delimited tags
     * @return Set of Strings representing mdc tags
     */
    public static Set<String> parseMdcTags(String mdcTagsString) {
        if (isNullOrEmpty(mdcTagsString)) {
            return Collections.emptySet();
        }

        return new HashSet<>(Arrays.asList(mdcTagsString.split(",")));
    }


    /**
     * Parses the provided string value into an integer value.
     * <p>If the string is null or empty this returns the default value.</p>
     *
     * @param value        value to parse
     * @param defaultValue default value
     * @return integer representation of provided value or default value.
     */
    public static Integer parseInteger(String value, Integer defaultValue) {
        if (isNullOrEmpty(value)) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    /**
     * Parses the provided string value into a long value.
     * <p>If the string is null or empty this returns the default value.</p>
     *
     * @param value        value to parse
     * @param defaultValue default value
     * @return long representation of provided value or default value.
     */
    public static Long parseLong(String value, Long defaultValue) {
        if (isNullOrEmpty(value)) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    /**
     * Parses the provided string value into a double value.
     * <p>If the string is null or empty this returns the default value.</p>
     *
     * @param value        value to parse
     * @param defaultValue default value
     * @return double representation of provided value or default value.
     */
    public static Double parseDouble(String value, Double defaultValue) {
        if (isNullOrEmpty(value)) {
            return defaultValue;
        }
        return Double.parseDouble(value);
    }

    /**
     * Trims a String, ensuring that the maximum length isn't reached.
     *
     * @param string string to trim
     * @param maxMessageLength maximum length of the string
     * @return trimmed string
     */
    public static String trimString(String string, int maxMessageLength) {
        if (string == null) {
            return null;
        } else if (string.length() > maxMessageLength) {
            // CHECKSTYLE.OFF: MagicNumber
            return string.substring(0, maxMessageLength - 3) + "...";
            // CHECKSTYLE.ON: MagicNumber
        } else {
            return string;
        }
    }

    /**
     * Serialize almost any object to JSON.
     *
     * @param generator JsonGenerator to write object out to.
     * @param value Value to write out.
     * @throws IOException On Jackson error (unserializable object).
     */
    public static void writeObjectToJson(JsonGenerator generator, Object value) throws IOException {
        if (value != null && value.getClass().isArray()) {
            // TODO: handle exception (byte[])
            value = Arrays.asList((Object[]) value);
        }

        // TODO: handle byte and byte[]
        // from python:
        // MAX_LENGTH_LIST = 50
        // MAX_LENGTH_STRING = 400
        // default frame allowance of 25
        // default 4k bytes of vars per frame, after that they are silently dropped
        // if all else fails, toString()

        if (value == null) {
            generator.writeNull();
        } else if (value instanceof Path) {
            // Path is weird because it implements Iterable, and then the iterator returns
            // more Paths, which are iterable... which would cause a stack overflow below.
            generator.writeString(value.toString());
        } else if (value instanceof Iterable) {
            // TODO: elide long iterables
            generator.writeStartArray();
            for (Object subValue : (Iterable<?>) value) {
                writeObjectToJson(generator, subValue);
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
                writeObjectToJson(generator, entry.getValue());
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

}
