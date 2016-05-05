package com.getsentry.raven.event;

import java.util.Date;

/**
 * An object that represents a single breadcrumb. Events may include a list
 * of breadcrumbs that help users re-create the path of actions that occurred
 * which lead to the Event happening.
 */
public class Breadcrumb {

    /**
     * (Optional) Type of the breadcrumb.
     */
    private final String type;
    /**
     * Timestamp when the breadcrumb occurred.
     */
    private final Date timestamp;
    /**
     * (Optional) Duration of the portion this breadcrumb represents.
     */
    private final float duration;
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
     * Create an immutable {@link Breadcrumb} object.
     *
     * @param type String
     * @param timestamp Date
     * @param duration float
     * @param level String
     * @param message String
     * @param category String
     */
    Breadcrumb(String type, Date timestamp, float duration, String level, String message, String category) {
        if (timestamp == null) {
            timestamp = new Date();
        }

        checkNotNull(level, "level");
        checkNotNull(message, "message");
        checkNotNull(category, "category");

        this.type = type;
        this.timestamp = timestamp;
        this.duration = duration;
        this.level = level;
        this.message = message;
        this.category = category;
    }

    private void checkNotNull(String str, String name) {
        if (str == null || str.trim().equals("")) {
            throw new IllegalArgumentException("field '" + name + "' is required but set to '" + str + "'");
        }
    }

    public String getType() {
        return type;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public float getDuration() {
        return duration;
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

}
