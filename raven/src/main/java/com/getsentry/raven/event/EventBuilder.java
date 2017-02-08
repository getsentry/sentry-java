package com.getsentry.raven.event;

import com.getsentry.raven.environment.RavenEnvironment;
import com.getsentry.raven.event.interfaces.SentryInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
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
    /**
     * Default hostname if it isn't set manually (or can't be determined).
     */
    public static final String DEFAULT_HOSTNAME = "unavailable";
    /**
     * Duration of the hostname caching.
     *
     * @see HostnameCache
     */
    public static final long HOSTNAME_CACHE_DURATION = TimeUnit.HOURS.toMillis(5);
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final HostnameCache HOSTNAME_CACHE = new HostnameCache(HOSTNAME_CACHE_DURATION);
    private final Event event;
    private boolean alreadyBuilt = false;

    /**
     * Creates a new EventBuilder to prepare a new {@link Event}.
     * <p>
     * Automatically generates the id of the new event.
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
        byte[] bytes = string.getBytes(UTF_8);
        Checksum checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length);
        return Long.toHexString(checksum.getValue()).toUpperCase();
    }

    /**
     * Sets default values for each field that hasn't been provided manually.
     *
     * @param event currently handled event.
     */
    private static void autoSetMissingValues(Event event) {
        // Ensure that a timestamp is set (to now at least!)
        if (event.getTimestamp() == null) {
            event.setTimestamp(new Date());
        }

        // Ensure that a platform is set
        if (event.getPlatform() == null) {
            event.setPlatform(DEFAULT_PLATFORM);
        }

        // Ensure that an SDK is set
        if (event.getSdkName() == null) {
            event.setSdkName(RavenEnvironment.SDK_NAME);
        }
        if (event.getSdkVersion() == null) {
            event.setSdkVersion(RavenEnvironment.SDK_VERSION);
        }

        // Ensure that a hostname is set
        if (event.getServerName() == null) {
            event.setServerName(HOSTNAME_CACHE.getHostname());
        }
    }

    /**
     * Ensures that every field in the {@code Event} are immutable to avoid confusion later.
     *
     * @param event event to make immutable.
     */
    private static void makeImmutable(Event event) {
        // Make the tags unmodifiable
        event.setTags(Collections.unmodifiableMap(event.getTags()));

        // Make the breadcrumbs unmodifiable
        event.setBreadcrumbs(Collections.unmodifiableList(event.getBreadcrumbs()));

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
    public EventBuilder withMessage(String message) {
        event.setMessage(message);
        return this;
    }

    /**
     * Sets the timestamp in the event.
     *
     * @param timestamp timestamp of the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withTimestamp(Date timestamp) {
        event.setTimestamp(timestamp);
        return this;
    }

    /**
     * Sets the log level in the event.
     *
     * @param level log level of the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withLevel(Event.Level level) {
        event.setLevel(level);
        return this;
    }

    /**
     * Sets application release version in the event.
     *
     * @param release application release version in the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withRelease(String release) {
        event.setRelease(release);
        return this;
    }

    /**
     * Sets application environment in the event.
     *
     * @param environment application environment in the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withEnvironment(String environment) {
        event.setEnvironment(environment);
        return this;
    }

    /**
     * Sets the logger in the event.
     *
     * @param logger logger of the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withLogger(String logger) {
        event.setLogger(logger);
        return this;
    }

    /**
     * Sets the platform in the event.
     *
     * @param platform platform of the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withPlatform(String platform) {
        event.setPlatform(platform);
        return this;
    }

    /**
     * Sets the SDK name in the event.
     *
     * @param sdkName name of the SDK that created the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withSdkName(String sdkName) {
        event.setSdkName(sdkName);
        return this;
    }

    /**
     * Sets the SDK version in the event.
     *
     * @param sdkVersion version of the SDK that created the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withSdkVersion(String sdkVersion) {
        event.setSdkVersion(sdkVersion);
        return this;
    }

    /**
     * Sets the culprit in the event based on a {@link StackTraceElement}.
     *
     * @param frame stack frame during which the event was captured.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withCulprit(StackTraceElement frame) {
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

        return withCulprit(sb.toString());
    }

    /**
     * Sets the culprit in the event.
     *
     * @param culprit culprit.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withCulprit(String culprit) {
        event.setCulprit(culprit);
        return this;
    }

    /**
     * Adds a tag to an event.
     * <p>
     * This allows to set a tag value in different contexts.
     *
     * @param tagKey   name of the tag.
     * @param tagValue value of the tag.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withTag(String tagKey, String tagValue) {
        event.getTags().put(tagKey, tagValue);
        return this;
    }

    /**
     * Adds a list of {@code Breadcrumb}s to the event.
     *
     * @param breadcrumbs list of breadcrumbs
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withBreadcrumbs(List<Breadcrumb> breadcrumbs) {
        event.setBreadcrumbs(breadcrumbs);
        return this;
    }

    /**
     * Sets the serverName in the event.
     *
     * @param serverName name of the server responsible for the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withServerName(String serverName) {
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
    public EventBuilder withExtra(String extraName, Object extraValue) {
        event.getExtra().put(extraName, extraValue);
        return this;
    }

    /**
     * Sets event fingerprint, an array of strings used to dictate the deduplicating for this event.
     *
     * @param fingerprint fingerprint
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withFingerprint(String... fingerprint) {
        List<String> list = new ArrayList<>(fingerprint.length);
        Collections.addAll(list, fingerprint);
        event.setFingerprint(list);
        return this;
    }

    /**
     * Sets event fingerprint, a list of strings used to dictate the deduplicating for this event.
     *
     * @param fingerprint fingerprint
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withFingerprint(List<String> fingerprint) {
        event.setFingerprint(fingerprint);
        return this;
    }

    /**
     * Generates a checksum from a given content and set it to the current event.
     *
     * @param contentToChecksum content to checksum.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withChecksumFor(String contentToChecksum) {
        return withChecksum(calculateChecksum(contentToChecksum));
    }

    /**
     * Sets the checksum for the current event.
     * <p>
     * It's recommended to rely instead on the checksum system provided by Sentry.
     *
     * @param checksum checksum for the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withChecksum(String checksum) {
        event.setChecksum(checksum);
        return this;
    }

    /**
     * Adds a {@link SentryInterface} to the event.
     * <p>
     * If a {@code SentryInterface} with the same interface name has already been added, the new one will replace
     * the old one.
     *
     * @param sentryInterface sentry interface to add to the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withSentryInterface(SentryInterface sentryInterface) {
        return withSentryInterface(sentryInterface, true);
    }

    /**
     * Adds a {@link SentryInterface} to the event.
     * <p>
     * Checks whether or not the entry already exists, and replaces it only if {@code replace} is true.
     *
     * @param sentryInterface sentry interface to add to the event.
     * @param replace         If true and a Sentry Interface with the same name has already been added it will be
     *                        replaced.
     *                        If false the statement will be ignored.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withSentryInterface(SentryInterface sentryInterface, boolean replace) {
        if (replace || !event.getSentryInterfaces().containsKey(sentryInterface.getInterfaceName())) {
            event.getSentryInterfaces().put(sentryInterface.getInterfaceName(), sentryInterface);
        }
        return this;
    }

    /**
     * Finalises the {@link Event} and returns it.
     * <p>
     * This operations will automatically set the missing values and make the mutable values immutable.
     *
     * @return an immutable event.
     */
    public synchronized Event build() {
        if (alreadyBuilt) {
            throw new IllegalStateException("A message can't be built twice");
        }

        autoSetMissingValues(event);
        makeImmutable(event);

        // Lock it only when everything has been set, in case of exception it should be possible to try to build again.
        alreadyBuilt = true;
        return event;
    }

    @Override
    public String toString() {
        return "EventBuilder{"
                + "event=" + event
                + ", alreadyBuilt=" + alreadyBuilt
                + '}';
    }


    /**
     * Sets the message in the event.
     *
     * @param message message of the event.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated Use {@link #withMessage(String)} instead.
     */
    @Deprecated
    public EventBuilder setMessage(String message) {
        return withMessage(message);
    }

    /**
     * Sets the timestamp in the event.
     *
     * @param timestamp timestamp of the event.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated Use {@link #withTimestamp(Date)} instead.
     */
    @Deprecated
    public EventBuilder setTimestamp(Date timestamp) {
        return withTimestamp(timestamp);
    }

    /**
     * Sets the log level in the event.
     *
     * @param level log level of the event.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated Use {@link #withLevel(Event.Level)} instead.
     */
    @Deprecated
    public EventBuilder setLevel(Event.Level level) {
        return withLevel(level);
    }

    /**
     * Sets the logger in the event.
     *
     * @param logger logger of the event.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated Use {@link #withLogger(String)} instead.
     */
    @Deprecated
    public EventBuilder setLogger(String logger) {
        return withLogger(logger);
    }

    /**
     * Sets the platform in the event.
     *
     * @param platform platform of the event.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated Use {@link #withPlatform(String)} instead.
     */
    @Deprecated
    public EventBuilder setPlatform(String platform) {
        return withPlatform(platform);
    }

    /**
     * Sets the culprit in the event based on a {@link StackTraceElement}.
     *
     * @param frame stack frame during which the event was captured.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated Use {@link #withCulprit(StackTraceElement)} instead.
     */
    @Deprecated
    public EventBuilder setCulprit(StackTraceElement frame) {
        return withCulprit(frame);
    }

    /**
     * Sets the culprit in the event.
     *
     * @param culprit culprit.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated Use {@link #withCulprit(String)} instead.
     */
    @Deprecated
    public EventBuilder setCulprit(String culprit) {
        return withCulprit(culprit);
    }

    /**
     * Adds a tag to an event.
     * <p>
     * This allows to set a tag value in different contexts.
     *
     * @param tagKey   name of the tag.
     * @param tagValue value of the tag.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated use {@link #withTag(String, String)} instead.
     */
    @Deprecated
    public EventBuilder addTag(String tagKey, String tagValue) {
        return withTag(tagKey, tagValue);
    }

    /**
     * Sets the serverName in the event.
     *
     * @param serverName name of the server responsible for the event.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated use {@link #withServerName(String)} instead.
     */
    @Deprecated
    public EventBuilder setServerName(String serverName) {
        return withServerName(serverName);
    }

    /**
     * Adds an extra property to the event.
     *
     * @param extraName  name of the extra property.
     * @param extraValue value of the extra property.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated use {@link #withExtra(String, Object)} instead.
     */
    @Deprecated
    public EventBuilder addExtra(String extraName, Object extraValue) {
        return withExtra(extraName, extraValue);
    }

    /**
     * Generates a checksum from a given content and set it to the current event.
     *
     * @param contentToChecksum content to checksum.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated use {@link #withChecksumFor(String)} instead.
     */
    @Deprecated
    public EventBuilder generateChecksum(String contentToChecksum) {
        return withChecksumFor(contentToChecksum);
    }

    /**
     * Sets the checksum for the current event.
     * <p>
     * It's recommended to rely instead on the checksum system provided by Sentry.
     *
     * @param checksum checksum for the event.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated use {@link #withChecksum(String)} instead.
     */
    @Deprecated
    public EventBuilder setChecksum(String checksum) {
        return withChecksum(checksum);
    }

    /**
     * Adds a {@link SentryInterface} to the event.
     * <p>
     * If a {@code SentryInterface} with the same interface name has already been added, the new one will replace
     * the old one.
     *
     * @param sentryInterface sentry interface to add to the event.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated use {@link #withSentryInterface(SentryInterface)} instead.
     */
    @Deprecated
    public EventBuilder addSentryInterface(SentryInterface sentryInterface) {
        return withSentryInterface(sentryInterface);
    }

    /**
     * Adds a {@link SentryInterface} to the event.
     * <p>
     * Checks whether or not the entry already exists, and replaces it only if {@code replace} is true.
     *
     * @param sentryInterface sentry interface to add to the event.
     * @param replace         If true and a Sentry Interface with the same name has already been added it will be
     *                        replaced.
     *                        If false the statement will be ignored.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated use {@link #withSentryInterface(SentryInterface, boolean)} instead.
     */
    @Deprecated
    public EventBuilder addSentryInterface(SentryInterface sentryInterface, boolean replace) {
        return withSentryInterface(sentryInterface, replace);
    }

    /**
     * Time sensitive cache in charge of keeping track of the hostname.
     * <p>
     * The {@code InetAddress.getLocalHost().getCanonicalHostName()} call can be quite expensive and could be called
     * for the creation of each {@link Event}. This system will prevent unnecessary costs by keeping track of the
     * hostname for a period defined during the construction.<br>
     * For performance purposes, the operation of retrieving the hostname will automatically fail after a period of
     * time
     * defined by {@link #GET_HOSTNAME_TIMEOUT} without result.
     */
    private static final class HostnameCache {
        /**
         * Time before the get hostname operation times out (in ms).
         */
        public static final long GET_HOSTNAME_TIMEOUT = TimeUnit.SECONDS.toMillis(1);
        private static final Logger logger = LoggerFactory.getLogger(HostnameCache.class);
        /**
         * Time for which the cache is kept.
         */
        private final long cacheDuration;
        /**
         * Current value for hostname (might change over time).
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
            FutureTask<String> futureTask = new FutureTask<>(new HostRetriever());
            try {
                new Thread(futureTask).start();
                logger.debug("Updating the hostname cache");
                hostname = futureTask.get(GET_HOSTNAME_TIMEOUT, TimeUnit.MILLISECONDS);
                expirationTimestamp = System.currentTimeMillis() + cacheDuration;
            } catch (Exception e) {
                futureTask.cancel(true);
                expirationTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(1);
                logger.warn("Localhost hostname lookup failed, keeping the value '{}'", hostname, e);
            }
        }

        /**
         * Task retrieving the current hostname.
         */
        private static final class HostRetriever implements Callable<String> {
            @Override
            public String call() throws Exception {
                return InetAddress.getLocalHost().getCanonicalHostName();
            }
        }
    }
}
