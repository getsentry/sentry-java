package net.kencochrane.raven.event;

import net.kencochrane.raven.event.interfaces.SentryInterface;

import java.util.*;

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
 * In order to ensure that a LoggedEvent can't be modified externally, the setters should have a package visibility.
 * </li>
 * <li>
 * A proper immutable Object should only contain immutable Objects and primitives, this must be ensured before making
 * publishing the LoggedEvent.
 * </li>
 * </ul>
 * </p>
 */
public class LoggedEvent {
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private final UUID eventId;
    private String message;
    // TODO: Rely on Joda time instead? (making it completely immutable!) Or wait for Java8 and JSR-310?
    private Date timestamp;
    private Level level;
    private String logger;
    private String platform;
    private String culprit;
    private Map<String, Set<String>> tags = new HashMap<String, Set<String>>();
    private String serverName;
    private String checksum;
    private Map<String, SentryInterface> sentryInterfaces = new HashMap<String, SentryInterface>();

    /**
     * Creates a new LoggedEvent (should be called only through {@link EventBuilder} with the specified identifier.
     *
     * @param eventId unique identifier of the event.
     */
    LoggedEvent(UUID eventId) {
        if (eventId == null)
            throw new IllegalArgumentException("The eventId can't be null");
        this.eventId = eventId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getMessage() {
        return message;
    }

    void setMessage(String message) {
        // TODO: Keep the message whatever the size is, and let the marshaller take care of cutting long messages?
        if (message != null && message.length() > MAX_MESSAGE_LENGTH)
            throw new IllegalArgumentException("A message can't be larger than " + MAX_MESSAGE_LENGTH + " characters");
        this.message = message;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getLogger() {
        return logger;
    }

    void setLogger(String logger) {
        this.logger = logger;
    }

    public Level getLevel() {
        return level;
    }

    void setLevel(Level level) {
        this.level = level;
    }

    public String getCulprit() {
        return culprit;
    }

    void setCulprit(String culprit) {
        this.culprit = culprit;
    }

    public String getChecksum() {
        return checksum;
    }

    void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Map<String, Set<String>> getTags() {
        return tags;
    }

    void setTags(Map<String, Set<String>> tags) {
        this.tags = tags;
    }

    public String getPlatform() {
        return platform;
    }

    void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getServerName() {
        return serverName;
    }

    void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public Map<String, SentryInterface> getSentryInterfaces() {
        return sentryInterfaces;
    }

    void setSentryInterfaces(Map<String, SentryInterface> sentryInterfaces) {
        this.sentryInterfaces = sentryInterfaces;
    }

    public static enum Level {
        FATAL,
        ERROR,
        WARNING,
        INFO,
        DEBUG
    }
}
