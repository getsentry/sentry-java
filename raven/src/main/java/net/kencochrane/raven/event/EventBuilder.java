package net.kencochrane.raven.event;

import net.kencochrane.raven.event.interfaces.SentryInterface;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * Builder to assist the creation of {@link Event}s.
 */
public class EventBuilder {
    /**
     * Default platform if it isn't set manually.
     */
    public static final String DEFAULT_PLATFORM = "java";
    public static final int HOSTNAME_CACHE_DURATION = 18000000;
    private static final HostnameCache hostnameCache = new HostnameCache(HOSTNAME_CACHE_DURATION);
    private final Event event;
    private boolean alreadyBuilt = false;

    /**
     * Creates a new EventBuilder to prepare a new {@link Event}.
     * <p>
     * Automatically generates the id of the new event.
     * </p>
     */
    public EventBuilder() {
        this(UUID.randomUUID());
    }

    /**
     * Creates a new EventBuilder to prepare a new {@link Event}.
     *
     * @param eventId unique identifier for the new event.
     */
    public EventBuilder(UUID eventId) {
        this.event = new Event(eventId);
    }

    /**
     * Calculates a checksum for a given string.
     *
     * @param string string from which a checksum should be obtained
     * @return a checksum allowing two events with the same properties to be grouped later.
     */
    private static String calculateChecksum(String string) {
        byte[] bytes = string.getBytes();
        Checksum checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length);
        return String.valueOf(checksum.getValue());
    }

    /**
     * Obtains the current hostname.
     *
     * @return the current hostname.
     */
    private static String getHostname() {
        return hostnameCache.getHostname();
    }

    /**
     * Sets default values for each field that hasn't been provided manually.
     *
     * @param event currently handled event.
     */
    private static void autoSetMissingValues(Event event) {
        // Ensure that a timestamp is set (to now at least!)
        if (event.getTimestamp() == null)
            event.setTimestamp(new Date());

        // Ensure that a platform is set
        if (event.getPlatform() == null)
            event.setPlatform(DEFAULT_PLATFORM);

        // Ensure that a hostname is set
        if (event.getServerName() == null)
            event.setServerName(getHostname());
    }

    /**
     * Ensures that every field in the {@code Event} are immutable to avoid confusion later.
     *
     * @param event event to make immutable.
     */
    private static void makeImmutable(Event event) {
        // Make the tags unmodifiable
        Map<String, Set<String>> unmodifiableTags = new HashMap<String, Set<String>>(event.getTags().size());
        for (Map.Entry<String, Set<String>> tag : event.getTags().entrySet()) {
            unmodifiableTags.put(tag.getKey(), Collections.unmodifiableSet(tag.getValue()));
        }
        event.setTags(Collections.unmodifiableMap(unmodifiableTags));

        // Make the extra properties unmodifiable (everything in it is still mutable though)
        event.setExtra(Collections.unmodifiableMap(event.getExtra()));

        // Make the SentryInterfaces unmodifiable
        event.setSentryInterfaces(Collections.unmodifiableMap(event.getSentryInterfaces()));
    }

    /**
     * Sets the message in the event.
     *
     * @param message message of the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder setMessage(String message) {
        event.setMessage(message);
        return this;
    }

    /**
     * Sets the timestamp in the event.
     *
     * @param timestamp timestamp of the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder setTimestamp(Date timestamp) {
        event.setTimestamp(timestamp);
        return this;
    }

    /**
     * Sets the log level in the event.
     *
     * @param level log level of the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder setLevel(Event.Level level) {
        event.setLevel(level);
        return this;
    }

    /**
     * Sets the logger in the event.
     *
     * @param logger logger of the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder setLogger(String logger) {
        event.setLogger(logger);
        return this;
    }

    /**
     * Sets the platform in the event.
     *
     * @param platform platform of the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder setPlatform(String platform) {
        event.setPlatform(platform);
        return this;
    }

    /**
     * Sets the culprit in the event based on a {@link StackTraceElement}.
     *
     * @param frame stack frame during which the event was captured.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder setCulprit(StackTraceElement frame) {
        StringBuilder sb = new StringBuilder();

        sb.append(frame.getClassName())
                .append(".")
                .append(frame.getMethodName());

        if (frame.getFileName() != null && !frame.getFileName().isEmpty()) {
            sb.append("(").append(frame.getFileName());
            if (frame.getLineNumber() >= 0) {
                sb.append(":").append(frame.getLineNumber());
            }
            sb.append(")");
        }

        return setCulprit(sb.toString());
    }

    /**
     * Sets the culprit in the event.
     *
     * @param culprit culprit.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder setCulprit(String culprit) {
        event.setCulprit(culprit);
        return this;
    }

    /**
     * Adds a tag to an event.
     * <p>
     * Multiple calls to {@code addTag} allow to have more that one value for a single tag.<br />
     * This allows to set a tag value in different contexts.
     * </p>
     *
     * @param tagKey   name of the tag.
     * @param tagValue value of the tag.
     * @return the current {@code EventBuilder} for chained calls.
     */
    //TODO: Check that the tag system works indeed this way.
    public EventBuilder addTag(String tagKey, String tagValue) {
        Set<String> tagValues = event.getTags().get(tagKey);
        if (tagValues == null) {
            tagValues = new HashSet<String>();
            event.getTags().put(tagKey, tagValues);
        }
        tagValues.add(tagValue);

        return this;
    }

    /**
     * Sets the serverName in the event.
     *
     * @param serverName name of the server responsible for the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder setServerName(String serverName) {
        event.setServerName(serverName);
        return this;
    }

    /**
     * Adds an extra property to the event.
     *
     * @param extraName  name of the extra property.
     * @param extraValue value of the extra property.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder addExtra(String extraName, Object extraValue) {
        event.getExtra().put(extraName, extraValue);
        return this;
    }

    /**
     * Generates a checksum from a given content and set it to the current event.
     *
     * @param contentToChecksum content to checksum.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder generateChecksum(String contentToChecksum) {
        return setChecksum(calculateChecksum(contentToChecksum));
    }

    /**
     * Sets the checksum for the current event.
     * <p>
     * It's recommended to rely instead on the checksum system provided by Sentry.
     * </p>
     *
     * @param checksum checksum for the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder setChecksum(String checksum) {
        event.setChecksum(checksum);
        return this;
    }

    /**
     * Adds a {@link SentryInterface} to the event.
     * <p>
     * If a {@code SentryInterface} with the same interface name has already been added, the new one will replace
     * the old one.
     * </p>
     *
     * @param sentryInterface sentry interface to add to the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder addSentryInterface(SentryInterface sentryInterface) {
        event.getSentryInterfaces().put(sentryInterface.getInterfaceName(), sentryInterface);
        return this;
    }

    /**
     * Finalises the {@link Event} and returns it.
     * <p>
     * This operations will automatically set the missing values and make the mutable values immutable.
     * </p>
     *
     * @return an immutable event.
     */
    public Event build() {
        if (alreadyBuilt)
            throw new IllegalStateException("A message can't be built twice");

        autoSetMissingValues(event);
        makeImmutable(event);

        // Lock it only when everything has been set, in case of exception it should be possible to try to build again.
        alreadyBuilt = true;
        return event;
    }

    /**
     * Time sensitive cache in charge of keeping track of the hostname.
     * <p>
     * The {@code InetAddress.getLocalHost().getCanonicalHostName()} call can be quite expensive and could be called
     * for the creation of each {@link Event}. This system will prevent unnecessary costs by keeping track of the
     * hostname for a period defined during the construction.
     * </p>
     */
    private static class HostnameCache {
        /**
         * Default hostname if it isn't set manually (or can't be determined).
         */
        public static final String DEFAULT_HOSTNAME = "unavailable";
        /**
         * Time for which the cache is kept.
         */
        private final long cacheDuration;
        /**
         * Current value for hostname (might change over time)
         */
        private String hostname = DEFAULT_HOSTNAME;
        /**
         * Time at which the cache should expire.
         */
        private long expirationTimestamp;

        /**
         * Sets up a cache for the hostname.
         *
         * @param cacheDuration cache duration in milliseconds.
         */
        private HostnameCache(long cacheDuration) {
            this.cacheDuration = cacheDuration;
        }

        /**
         * Gets the hostname of the current machine.
         * <p>
         * Gets the value from the cache if possible otherwise calls {@link #updateCache()}.
         * </p>
         *
         * @return the hostname of the current machine.
         */
        public String getHostname() {
            if (expirationTimestamp < System.currentTimeMillis()) {
                updateCache();
            }

            return hostname;
        }

        /**
         * Force an update of the cache to get the current value of the hostname.
         */
        public void updateCache() {
            expirationTimestamp = System.currentTimeMillis() + cacheDuration;

            try {
                hostname = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                hostname = DEFAULT_HOSTNAME;
            }
        }
    }
}
