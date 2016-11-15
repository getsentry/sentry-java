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
    private final String type;
    /**
     * Timestamp when the breadcrumb occurred.
     */
    private final Date timestamp;
    /**
     * Level of the breadcrumb.
     */
    private final String level;
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
     * Create an immutable {@link Breadcrumb} object.
     *
     * @param type String
     * @param timestamp Date
     * @param level String
     * @param message String
     * @param category String
     * @param data Map of String to String
     */
    Breadcrumb(String type, Date timestamp, String level, String message,
        String category, Map<String, String> data) {

        if (timestamp == null) {
            timestamp = new Date();
        }

        checkNotNull(level, "level");
        checkNotNull(category, "category");

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

    private void checkNotNull(String str, String name) {
        if (str == null) {
            throw new IllegalArgumentException("field '" + name + "' is required but got null");
        }
    }

    public String getType() {
        return type;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
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
