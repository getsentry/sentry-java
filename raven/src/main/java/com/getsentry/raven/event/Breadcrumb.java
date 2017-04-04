package com.getsentry.raven.event;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * An object that represents a single breadcrumb. Events may include a list
 * of breadcrumbs that help users re-create the path of actions that occurred
 * which lead to the Event happening.
 */
public class Breadcrumb implements Serializable {

    /**
     * (Optional) Type of the breadcrumb.
     */
    private final Type type;
    /**
     * Timestamp when the breadcrumb occurred.
     */
    private final Date timestamp;
    /**
     * Level of the breadcrumb.
     */
    private final Level level;
    /**
     * Message of the breadcrumb.
     */
    private final String message;
    /**
     * Category of the breadcrumb.
     */
    private final String category;
    /**
     * Data related to the breadcrumb.
     */
    private final Map<String, String> data;

    /**
     * Possible choices for the level field.
     */
    public enum Level {
        /**
         * DEBUG level.
         */
        DEBUG("debug"),

        /**
         * INFO level.
         */
        INFO("info"),

        /**
         * WARNING level.
         */
        WARNING("warning"),

        /**
         * ERROR level.
         */
        ERROR("error"),

        /**
         * CRITICAL level.
         */
        CRITICAL("critical");

        private final String value;

        /**
         * Construct a {@link Level} with the value to serialize with.
         *
         * @param value Value to use for serialization.
         */
        Level(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
    /**
     * Possible choices for the type field.
     */
    public enum Type {
        /**
         * DEFAULT type.
         */
        DEFAULT("default"),

        /**
         * HTTP type.
         */
        HTTP("http"),

        /**
         * NAVIGATION type.
         */
        NAVIGATION("navigation");

        private final String value;

        /**
         * Construct a {@link Type} with the value to serialize with.
         *
         * @param value Value to use for serialization.
         */
        Type(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Create an immutable {@link Breadcrumb} object.
     *
     * @param type Type
     * @param timestamp Date
     * @param level Level
     * @param message String
     * @param category String
     * @param data Map of String to String
     */
    Breadcrumb(Type type, Date timestamp, Level level, String message,
        String category, Map<String, String> data) {

        if (timestamp == null) {
            timestamp = new Date();
        }

        if (message == null && (data == null || data.size() < 1)) {
            throw new IllegalArgumentException("one of 'message' or 'data' must be set");
        }

        this.type = type;
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.category = category;
        this.data = data;
    }

    public Type getType() {
        return type;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public Level getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getCategory() {
        return category;
    }

    public Map<String, String> getData() {
        return data;
    }

}
