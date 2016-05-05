package com.getsentry.raven.event;

import java.util.Date;

/**
 * Builder to assist the creation of {@link Breadcrumb}s.
 */
public class BreadcrumbBuilder {

    private String type;
    private Date timestamp;
    private float duration;
    private String level;
    private String message;
    private String category;

    /**
     * Type of the {@link Breadcrumb}.
     *
     * @param newType String
     * @return current BreadcrumbBuilder
     */
    public BreadcrumbBuilder setType(String newType) {
        this.type = newType;
        return this;
    }

    /**
     * Timestamp of the {@link Breadcrumb}.
     *
     * @param newTimestamp Date
     * @return current BreadcrumbBuilder
     */
    public BreadcrumbBuilder setTimestamp(Date newTimestamp) {
        this.timestamp = newTimestamp;
        return this;
    }

    /**
     * Duration of the {@link Breadcrumb}.
     *
     * @param newDuration float
     * @return current BreadcrumbBuilder
     */
    public BreadcrumbBuilder setDuration(float newDuration) {
        this.duration = newDuration;
        return this;
    }

    /**
     * Level of the {@link Breadcrumb}.
     *
     * @param newLevel String
     * @return current BreadcrumbBuilder
     */
    public BreadcrumbBuilder setLevel(String newLevel) {
        this.level = newLevel;
        return this;
    }

    /**
     * Message of the {@link Breadcrumb}.
     *
     * @param newMessage String
     * @return current BreadcrumbBuilder
     */
    public BreadcrumbBuilder setMessage(String newMessage) {
        this.message = newMessage;
        return this;
    }

    /**
     * Category of the {@link Breadcrumb}.
     *
     * @param newCategory String
     * @return current BreadcrumbBuilder
     */
    public BreadcrumbBuilder setCategory(String newCategory) {
        this.category = newCategory;
        return this;
    }

    /**
     * Build and return the {@link Breadcrumb} object.
     *
     * @return Breadcrumb
     */
    public Breadcrumb build() {
        return new Breadcrumb(type, timestamp, duration, level, message, category);
    }

}
