package io.sentry.event;

import io.sentry.environment.SentryEnvironment;
import io.sentry.event.interfaces.SentryInterface;
import io.sentry.event.interfaces.SentryStackTraceElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private Set<String> sdkIntegrations = new HashSet<>();

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
     */
    private void autoSetMissingValues() {
        // Ensure that a timestamp is set (to now at least!)
        if (event.getTimestamp() == null) {
            event.setTimestamp(new Date());
        }

        // Ensure that a platform is set
        if (event.getPlatform() == null) {
            event.setPlatform(DEFAULT_PLATFORM);
        }

        // Ensure that an SDK is set
        if (event.getSdk() == null) {
            event.setSdk(new Sdk(SentryEnvironment.SDK_NAME, SentryEnvironment.SDK_VERSION,
                sdkIntegrations));
        }

        // Ensure that a hostname is set
        if (event.getServerName() == null) {
            event.setServerName(HOSTNAME_CACHE.getHostname());
        }
    }

    /**
     * Ensures that every field in the {@code Event} are immutable to avoid confusion later.
     */
    private void makeImmutable() {
        // Make the tags unmodifiable
        event.setTags(Collections.unmodifiableMap(event.getTags()));

        // Make the breadcrumbs unmodifiable
        event.setBreadcrumbs(Collections.unmodifiableList(event.getBreadcrumbs()));

        // Make the contexts unmodifiable
        Map<String, Map<String, Object>> tempContexts = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> contextEntry : event.getContexts().entrySet()) {
            tempContexts.put(contextEntry.getKey(), Collections.unmodifiableMap(contextEntry.getValue()));
        }
        event.setContexts(Collections.unmodifiableMap(tempContexts));

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
     * Sets application distribution version in the event.
     *
     * @param dist application distribution version in the event.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withDist(String dist) {
        event.setDist(dist);
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
     * Add an integration to the {@link Sdk}.
     *
     * @param integration Name of the integration used.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withSdkIntegration(String integration) {
        sdkIntegrations.add(integration);
        return this;
    }



    /**
     * Sets the culprit in the event based on a {@link SentryStackTraceElement}.
     *
     * @param frame stack frame during which the event was captured.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated Culprit has been removed in favor of Transaction.
     */
    @Deprecated
    public EventBuilder withCulprit(SentryStackTraceElement frame) {
        return withCulprit(buildCulpritString(frame.getModule(), frame.getFunction(),
            frame.getFileName(), frame.getLineno()));
    }


    /**
     * Sets the culprit in the event based on a {@link StackTraceElement}.
     *
     * @param frame stack frame during which the event was captured.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated Culprit has been removed in favor of Transaction.
     */
    @Deprecated
    public EventBuilder withCulprit(StackTraceElement frame) {
        return withCulprit(buildCulpritString(frame.getClassName(), frame.getMethodName(),
            frame.getFileName(), frame.getLineNumber()));
    }

    private String buildCulpritString(String className, String methodName, String fileName, int lineNumber) {
        StringBuilder sb = new StringBuilder();

        sb.append(className)
            .append(".")
            .append(methodName);

        if (fileName != null && !fileName.isEmpty()) {
            sb.append("(").append(fileName);
            if (lineNumber >= 0) {
                sb.append(":").append(lineNumber);
            }
            sb.append(")");
        }

        return sb.toString();
    }

    /**
     * Sets the culprit in the event.
     *
     * @param culprit culprit.
     * @return the current {@code EventBuilder} for chained calls.
     * @deprecated Culprit has been removed in favor of Transaction.
     */
    @Deprecated
    public EventBuilder withCulprit(String culprit) {
        event.setCulprit(culprit);
        return this;
    }

    /**
     * Sets the transaction in the event.
     *
     * @param transaction transaction.
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withTransaction(String transaction) {
        event.setTransaction(transaction);
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
     * Adds a map of map of context objects to the event.
     *
     * @param contexts map of map of contexts
     * @return the current {@code EventBuilder} for chained calls.
     */
    public EventBuilder withContexts(Map<String, Map<String, Object>> contexts) {
        event.setContexts(contexts);
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

        autoSetMissingValues();
        makeImmutable();

        // Lock it only when everything has been set, in case of exception it should be possible to try to build again.
        alreadyBuilt = true;
        return event;
    }

    public Event getEvent() {
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
        private volatile String hostname = DEFAULT_HOSTNAME;
        /**
         * Time at which the cache should expire.
         */
        private volatile long expirationTimestamp;
        /**
         * Whether a cache update thread is currently running or not.
         */
        private AtomicBoolean updateRunning = new AtomicBoolean(false);

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
            if (expirationTimestamp < System.currentTimeMillis()
                && updateRunning.compareAndSet(false, true)) {
                updateCache();
            }

            return hostname;
        }

        /**
         * Force an update of the cache to get the current value of the hostname.
         */
        public void updateCache() {
            Callable<Void> hostRetriever = new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    try {
                        hostname = InetAddress.getLocalHost().getCanonicalHostName();
                        expirationTimestamp = System.currentTimeMillis() + cacheDuration;
                    } finally {
                        updateRunning.set(false);
                    }

                    return null;
                }
            };

            try {
                logger.debug("Updating the hostname cache");
                FutureTask<Void> futureTask = new FutureTask<>(hostRetriever);
                new Thread(futureTask).start();
                futureTask.get(GET_HOSTNAME_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                expirationTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(1);
                logger.debug("Localhost hostname lookup failed, keeping the value '{}'."
                    + " If this persists it may mean your DNS is incorrectly configured and"
                    + " you may want to hardcode your server name: https://docs.sentry.io/clients/java/config/",
                    hostname, e);
            }
        }
    }
}
