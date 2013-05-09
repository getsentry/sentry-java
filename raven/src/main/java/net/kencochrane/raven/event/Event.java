package net.kencochrane.raven.event;

import net.kencochrane.raven.event.interfaces.SentryInterface;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plain Old Java Object describing an event that will be sent to a Sentry instance.
 * <p>
 * For security purposes, an event should be created from an {@link EventBuilder} only, and be completely immutable
 * once it has been fully generated.
 * </p>
 * <p>
 * Notes to developers:
 * <ul>
 * <li>
 * In order to ensure that a Event can't be modified externally, the setters should have a package visibility.
 * </li>
 * <li>
 * A proper immutable Object should only contain immutable Objects and primitives, this must be ensured before
 * publishing the Event.<br />
 * There is one exception, the {@link #extra} section can't be transformed to be completely immutable.
 * </li>
 * </ul>
 * </p>
 */
public class Event implements Serializable {
    /**
     * Unique identifier of the event.
     */
    private final UUID id;
    /**
     * User-readable representation of this event.
     */
    private String message;
    /**
     * Exact time when the logging occurred.
     */
    private Date timestamp;
    /**
     * The record severity.
     */
    private Level level;
    /**
     * The name of the logger which created the record.
     */
    private String logger;
    /**
     * A string representing the currently used platform (java/python).
     */
    private String platform;
    /**
     * Function call which was the primary perpetrator of this event.
     */
    private String culprit;
    /**
     * A map or list of tags for this event.
     * <p>
     * Automatically created with a Map that is made unmodifiable by the {@link EventBuilder}.
     * </p>
     */
    private Map<String, String> tags = new HashMap<String, String>();
    /**
     * Identifies the host client from which the event was recorded.
     */
    private String serverName;
    /**
     * A map or list of additional properties for this event.
     * <p>
     * Automatically created with a Map that is made unmodifiable by the {@link EventBuilder}.
     * </p>
     */
    private Map<String, Object> extra = new HashMap<String, Object>();
    /**
     * Checksum for the event, allowing to group events with a similar checksum.
     */
    private String checksum;
    /**
     * Additional interfaces for other information and metadata.
     * <p>
     * Automatically created with a Map that is made unmodifiable by the {@link EventBuilder}.
     * </p>
     */
    private Map<String, SentryInterface> sentryInterfaces = new HashMap<String, SentryInterface>();

    /**
     * Creates a new Event (should be called only through {@link EventBuilder} with the specified identifier.
     *
     * @param id unique identifier of the event.
     */
    Event(UUID id) {
        if (id == null)
            throw new IllegalArgumentException("The id can't be null");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    void setMessage(String message) {
        this.message = message;
    }

    public Date getTimestamp() {
        return (timestamp != null) ? (Date) timestamp.clone() : null;
    }

    void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Level getLevel() {
        return level;
    }

    void setLevel(Level level) {
        this.level = level;
    }

    public String getLogger() {
        return logger;
    }

    void setLogger(String logger) {
        this.logger = logger;
    }

    public String getPlatform() {
        return platform;
    }

    void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getCulprit() {
        return culprit;
    }

    void setCulprit(String culprit) {
        this.culprit = culprit;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public String getServerName() {
        return serverName;
    }

    void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public String getChecksum() {
        return checksum;
    }

    void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Map<String, SentryInterface> getSentryInterfaces() {
        return sentryInterfaces;
    }

    void setSentryInterfaces(Map<String, SentryInterface> sentryInterfaces) {
        this.sentryInterfaces = sentryInterfaces;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return id.equals(((Event) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Event{"
                + "level=" + level
                + ", message='" + message + '\''
                + ", logger='" + logger + '\''
                + '}';
    }

    /**
     * Levels of log available in Sentry.
     */
    public static enum Level {
        /**
         * Fatal is the highest form of log available, use it for unrecoverable issues.
         */
        FATAL,
        /**
         * Error denotes an unexpected behaviour that prevented the code to work properly.
         */
        ERROR,
        /**
         * Warning should be used to define logs generated by expected and handled bad behaviour.
         */
        WARNING,
        /**
         * Info is used to give general details on the running application, usually only messages.
         */
        INFO,
        /**
         * Debug information to track every detail of the application execution process.
         */
        DEBUG
    }
}
