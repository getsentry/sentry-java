package net.kencochrane.raven.event;

import net.kencochrane.raven.event.interfaces.SentryInterface;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * Builder to assist the creation of {@link LoggedEvent}s.
 */
public class EventBuilder {
    /**
     * Default log level if it isn't set manually.
     */
    public static final LoggedEvent.Level DEFAULT_LEVEL = LoggedEvent.Level.ERROR;
    /**
     * Default logger if it isn't set manually.
     */
    public static final String DEFAULT_LOGGER = "root";
    /**
     * Default platform if it isn't set manually.
     */
    public static final String DEFAULT_PLATFORM = "java";
    /**
     * Default hostname if it isn't set manually (or can't be determined).
     */
    public static final String DEFAULT_HOSTNAME = "unavailable";
    /**
     * Default log level if it isn't provided (either directly or through an exception).
     */
    public static final String DEFAULT_MESSAGE = "(empty)";
    private final LoggedEvent event;
    private boolean alreadyBuilt = false;

    public EventBuilder() {
        this(UUID.randomUUID());
    }

    public EventBuilder(UUID eventId) {
        this.event = new LoggedEvent(eventId);
    }

    private static String calculateChecksum(String message) {
        // TODO: Large strings will be poorly handled
        byte[] bytes = message.getBytes();
        Checksum checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length);
        return String.valueOf(checksum.getValue());
    }

    private static String getHostname() {
        try {
            // TODO: Cache this info
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            return DEFAULT_HOSTNAME;
        }
    }

    private static void autoSetMissingValues(LoggedEvent event) {
        // Ensure that an actual message is set
        if (event.getMessage() == null)
            event.setMessage(DEFAULT_MESSAGE);

        // Ensure that a timestamp is set (to now at least!)
        if (event.getTimestamp() == null)
            event.setTimestamp(new Date());

        // Ensure that a log level is set
        if (event.getLevel() == null)
            event.setLevel(DEFAULT_LEVEL);

        // Ensure that a logger is set
        if (event.getLogger() == null)
            event.setLogger(DEFAULT_LOGGER);

        // Ensure that a platform is set
        if (event.getPlatform() == null)
            event.setPlatform(DEFAULT_PLATFORM);

        // Ensure that a hostname is set
        if (event.getServerName() == null)
            event.setServerName(getHostname());

        // TODO: As the server can figure that itself, wouldn't it be better to assume that if no checksum is given
        // the server will be in charge of generating it?

        // Ensure that a checksum is present
        if (event.getChecksum() == null)
            event.setChecksum(calculateChecksum(event.getMessage()));
    }

    private static void makeImmutable(LoggedEvent event) {
        // Make the tags unmodifiable
        Map<String, Set<String>> unmodifiablesTags = new HashMap<String, Set<String>>(event.getTags().size());
        for (Map.Entry<String, Set<String>> tag : event.getTags().entrySet()) {
            unmodifiablesTags.put(tag.getKey(), Collections.unmodifiableSet(tag.getValue()));
        }
        event.setTags(Collections.unmodifiableMap(unmodifiablesTags));

        // Make the SentryInterfaces unmodifiable
        event.setSentryInterfaces(Collections.unmodifiableMap(event.getSentryInterfaces()));
    }

    private static String determineCulprit(Throwable throwable) {
        Throwable currentThrowable = throwable;
        String culprit = null;
        while (currentThrowable != null) {
            StackTraceElement[] elements = currentThrowable.getStackTrace();
            if (elements.length > 0) {
                StackTraceElement trace = elements[0];
                culprit = trace.getClassName() + "." + trace.getMethodName();
            }
            currentThrowable = currentThrowable.getCause();
        }
        return culprit;
    }

    public EventBuilder addTag(String tagKey, String tagValue) {
        Set<String> tagValues = event.getTags().get(tagKey);
        if (tagValues == null) {
            tagValues = new HashSet<String>();
            event.getTags().put(tagKey, tagValues);
        }
        tagValues.add(tagValue);

        return this;
    }

    public EventBuilder setCulprit(Throwable throwable) {
        return setCulprit(determineCulprit(throwable));
    }

    public EventBuilder setCulprit(String culprit) {
        event.setCulprit(culprit);
        return this;
    }

    public EventBuilder addSentryInterface(SentryInterface sentryInterface) {
        event.getSentryInterfaces().put(sentryInterface.getInterfaceName(), sentryInterface);
        return this;
    }

    /**
     * Manually set the checksum for the current event.
     * <p>
     * It's recommended to use instead {@link #generateChecksume(String)} which will avoid any problem with
     * the checksum generation.
     * </p>
     *
     * @param checksum
     * @return
     */
    public EventBuilder setChecksum(String checksum) {
        event.setChecksum(checksum);
        return this;
    }

    /**
     *
     * @param contentToCheckSum
     * @return
     */
    public EventBuilder generateChecksum(String contentToCheckSum) {
        return setChecksum(calculateChecksum(contentToCheckSum));
    }

    /**
     * Finalise the {@link LoggedEvent} and returns it.
     * <p>
     * This operations will automatically set the missing values and make the mutable values immutable.
     * </p>
     *
     * @return an immutable event.
     */
    public LoggedEvent build() {
        if (alreadyBuilt)
            throw new IllegalStateException("A message can't be built twice");

        autoSetMissingValues(event);
        makeImmutable(event);

        // Lock it only when everything has been set, in case of exception it should be possible to try to build again.
        alreadyBuilt = true;
        return event;
    }
}
