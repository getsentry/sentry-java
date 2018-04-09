package io.sentry.event;

import io.sentry.event.interfaces.SentryInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plain Old Java Object describing an event that will be sent to a Sentry server.
 * <p>
 * For security purposes, an event should be created from an {@link EventBuilder} only, and be completely immutable
 * once it has been fully generated.
 * <p>
 * Notes to developers:
 * <ul>
 * <li>
 * In order to ensure that a Event can't be modified externally, the setters should have a package visibility.
 * <li>
 * A proper immutable Object should only contain immutable Objects and primitives, this must be ensured before
 * publishing the Event.<br>
 * There is one exception, the {@link #extra} section can't be transformed to be completely immutable.
 * </ul>
 */
public class Event implements Serializable {
    private static final Logger _logger = LoggerFactory.getLogger(Event.class);
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
     * An {@link Sdk} instance representing the version and integrations used to send
     * the event.
     */
    private Sdk sdk;
    /**
     * Function call which was the primary perpetrator of this event.
     */
    private String culprit;
    /**
     * Name of the transaction that this event occurred inside of.
     */
    private String transaction;
    /**
     * A map or list of tags for this event.
     * <p>
     * Automatically created with a Map that is made unmodifiable by the {@link EventBuilder}.
     */
    private Map<String, String> tags = new HashMap<>();
    /**
     * List of Breadcrumb objects related to the event.
     */
    private List<Breadcrumb> breadcrumbs = new ArrayList<>();
    /**
     * Map of map of context objects related to the event.
     */
    private Map<String, Map<String, Object>> contexts = new HashMap<>();
    /**
     * Identifies the version of the application.
     */
    private String release;
    /**
     * Identifies the distribution of the application.
     */
    private String dist;
    /**
     * Identifies the environment the application is running in.
     */
    private String environment;
    /**
     * Identifies the host client from which the event was recorded.
     */
    private String serverName;
    /**
     * A map or list of additional properties for this event.
     * <p>
     * Automatically created with a Map that is made unmodifiable by the {@link EventBuilder}.
     * <p>
     * This transient map may contain objects which aren't serializable. They will be automatically be taken care of
     * by {@link #readObject(ObjectInputStream)} and {@link #writeObject(ObjectOutputStream)}.
     */
    private transient Map<String, Object> extra = new HashMap<>();
    /**
     * Event fingerprint, a list of strings used to dictate the deduplicating for this event.
     */
    private List<String> fingerprint;
    /**
     * Checksum for the event, allowing to group events with a similar checksum.
     */
    private String checksum;
    /**
     * Additional interfaces for other information and metadata.
     * <p>
     * Automatically created with a Map that is made unmodifiable by the {@link EventBuilder}.
     */
    private Map<String, SentryInterface> sentryInterfaces = new HashMap<>();

    /**
     * Creates a new Event (should be called only through {@link EventBuilder} with the specified identifier.
     *
     * @param id unique identifier of the event.
     */
    Event(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("The id can't be null");
        }
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

    public Sdk getSdk() {
        return sdk;
    }

    public void setSdk(Sdk sdk) {
        this.sdk = sdk;
    }

    public String getCulprit() {
        return culprit;
    }

    /**
     * Sets the culprit.
     *
     * @param culprit Function call which was the primary perpetrator of this event.
     * @deprecated Culprit has been removed in favor of Transaction.
     */
    @Deprecated
    void setCulprit(String culprit) {
        this.culprit = culprit;
    }

    public String getTransaction() {
        return transaction;
    }

    void setTransaction(String transaction) {
        this.transaction = transaction;
    }

    public List<Breadcrumb> getBreadcrumbs() {
        return breadcrumbs;
    }

    void setBreadcrumbs(List<Breadcrumb> breadcrumbs) {
        this.breadcrumbs = breadcrumbs;
    }

    public Map<String, Map<String, Object>> getContexts() {
        return contexts;
    }

    public void setContexts(Map<String, Map<String, Object>> contexts) {
        this.contexts = contexts;
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

    public String getRelease() {
        return release;
    }

    void setRelease(String release) {
        this.release = release;
    }

    public String getDist() {
        return dist;
    }

    public void setDist(String dist) {
        this.dist = dist;
    }

    public String getEnvironment() {
        return environment;
    }

    void setEnvironment(String environment) {
        this.environment = environment;
    }

    //CHECKSTYLE.OFF: JavadocMethod
    public Map<String, Object> getExtra() {
        if (extra == null) {
            // `extra` *should* never be null, but there are scenarios such as
            // when an application is run through ProGuard which may cause deserialization
            // code to be removed (and thus silently not run). In this case, our overridden
            // `readObject` may never be called and `extra` will remain null. :(
            extra = new HashMap<>();
            _logger.warn("`extra` field was null, deserialization must not have been called,"
                    + " please check your ProGuard (or other obfuscation) configuration.");
        }

        return extra;
    }
    //CHECKSTYLE.ON: JavadocMethod

    void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public List<String> getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(List<String> fingerprint) {
        this.fingerprint = fingerprint;
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

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        extra = (Map<String, Object>) stream.readObject();
    }

    private void writeObject(ObjectOutputStream stream)
        throws IOException {
        stream.defaultWriteObject();
        stream.writeObject(convertToSerializable(extra));
    }

    /**
     * Returns a serializable Map (HashMap) with the content of the parameter Map.
     * <p>
     * Serializable objects are kept as is in the Map, while the non serializable ones are converted into string
     * using the {@code toString()} method.
     *
     * @param objectMap original Map containing various Objects.
     * @return A serializable map which contains only serializable entries.
     */
    //CHECKSTYLE.OFF: IllegalType
    private static HashMap<String, ? super Serializable> convertToSerializable(Map<String, Object> objectMap) {
        HashMap<String, ? super Serializable> serializableMap = new HashMap<>(objectMap.size());
        for (Map.Entry<String, Object> objectEntry : objectMap.entrySet()) {
            if (objectEntry.getValue() == null) {
                serializableMap.put(objectEntry.getKey(), (String) null);
            } else if (objectEntry.getValue() instanceof Serializable) {
                serializableMap.put(objectEntry.getKey(), (Serializable) objectEntry.getValue());
            } else {
                serializableMap.put(objectEntry.getKey(), objectEntry.getValue().toString());
            }
        }
        return serializableMap;
    }
    //CHECKSTYLE.ON: IllegalType

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

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
    public enum Level {
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
