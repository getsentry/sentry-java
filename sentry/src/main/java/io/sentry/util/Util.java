package io.sentry.util;

import java.io.Closeable;
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
     * Try to remove the shutDownHook, handling the case where the VM is in shutdown process.
     * @param shutDownHook the shutDownHook to remove
     * @return true if the hook was removed, false otherwise
     */
    public static boolean safelyRemoveShutdownHook(Thread shutDownHook) {
        try {
            return Runtime.getRuntime().removeShutdownHook(shutDownHook);
        } catch (IllegalStateException e) {
            // CHECKSTYLE.OFF: EmptyBlock
            if (e.getMessage().equals("Shutdown in progress")) {
                // ignore
            } else {
                throw e;
            }
            // CHECKSTYLE.ON: EmptyBlock
        }
        return false;
    }

    /**
     * Closes an instance of Closeable and suppress all possible raised Exceptions.
     * @param closeable an instance that needs to be closed. null value is supported.
     */
    public static void closeQuietly(Closeable closeable) {
        // CHECKSTYLE.OFF: EmptyCatchBlock
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
        // CHECKSTYLE.ON: EmptyCatchBlock
    }
}
