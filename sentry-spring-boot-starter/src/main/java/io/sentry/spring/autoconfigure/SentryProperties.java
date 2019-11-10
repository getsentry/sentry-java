package io.sentry.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for Sentry, example:
 *
 * <pre>
 * sentry:
 *     enabled: true
 *     init-default-client: true
 *     dsn: https://00059966e6224d03a77ea5eca10fbe18@sentry.mycompany.com/14
 *     release: "1.0.1"
 *     dist: x86
 *     environment: staging
 *     server-name: megaServer
 *     tags:
 *         firstTag: Hello
 *         secondTag: Awesome
 *     mdc-tags: [mdcTagA, mdcTagB]
 *     extra:
 *         extraTag: extra
 * </pre>
 */
@ConfigurationProperties("sentry")
public class SentryProperties {

    /**
     * Whether to enable sentry.
     */
    private boolean enabled = true;

    /**
     * Whether to initialize Sentry Client as a bean.
     */
    private boolean initDefaultClient = true;

    /**
     * Data source name
     * All of the options can be configured by setting querystring parameters on the DSN itself.
     * https://docs.sentry.io/clients/java/config/#configuration-via-the-dsn
     * More information about configuration via DSN https://docs.sentry.io/clients/java/config/#configuration-via-the-dsn
     */
    private URL dsn;

    /**
     * The application version that will be sent with each event.
     */
    private String release;

    /**
     * The application distribution that will be sent with each event.
     * Note that the distribution is only useful (and used) if the release is also set.
     */
    private String dist;

    /**
     * The application environment that will be sent with each event.
     */
    private String environment;

    /**
     * The server name that will be sent with each event.
     */
    private String serverName;

    /**
     * Tags that will be sent with each event.
     */
    private Map<String, String> tags = new LinkedHashMap<>();

    /**
     * Set tag names that are extracted from the SLF4J MDC system.
     */
    private Set<String> mdcTags = new HashSet<>();

    /**
     * Set extra data that will be sent with each event (but not as tags).
     */
    private Map<String, Object> extra = new LinkedHashMap<>();

    /**
     * By default the content sent to Sentry is compressed before being sent. However, compressing and encoding the
     * data adds a small CPU and memory hit which might not be useful if the connection to Sentry is fast and reliable.
     * Depending on the limitations of the project (e.g. a mobile application with a limited connection, Sentry hosted
     * on an external network), it can be useful to compress the data beforehand or not.
     */
    private Boolean compression;

    /**
     * By default only the first 1000 characters of a message will be sent to the server.
     */
    private Integer maxMessageLength;

    /**
     * A timeout is set to avoid blocking Sentry threads because establishing a connection is taking too long.
     */
    private Integer timeout;

    /**
     * Sentry can be configured to sample events.
     * This option takes a number from 0.0 to 1.0, representing the percent of events to allow through to server
     * (from 0% to 100%). By default all events will be sent to the Sentry server.
     */
    private Double sampleRate;

    /**
     * By default, an UncaughtExceptionHandler is configured that will attempt to send exceptions to Sentry.
     * Exceptions are sent asynchronously by default, and there is no guarantee they will be sent before the JVM exits.
     * This option is best used in conjunction with the disk buffering system.
     */
    private Boolean uncaughtHandlerEnabled;

    private final Stacktrace stacktrace = new Stacktrace();
    private final Buffer buffer = new Buffer();
    private final Async async = new Async();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInitDefaultClient() {
        return initDefaultClient;
    }

    public void setInitDefaultClient(boolean initDefaultClient) {
        this.initDefaultClient = initDefaultClient;
    }

    public URL getDsn() {
        return dsn;
    }

    public void setDsn(URL dsn) {
        this.dsn = dsn;
    }

    public String getRelease() {
        return release;
    }

    public void setRelease(String release) {
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

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public Set<String> getMdcTags() {
        return mdcTags;
    }

    public void setMdcTags(Set<String> mdcTags) {
        this.mdcTags = mdcTags;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public Boolean getCompression() {
        return compression;
    }

    public void setCompression(Boolean compression) {
        this.compression = compression;
    }

    public Integer getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(Integer maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(Double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public Boolean getUncaughtHandlerEnabled() {
        return uncaughtHandlerEnabled;
    }

    public void setUncaughtHandlerEnabled(Boolean uncaughtHandlerEnabled) {
        this.uncaughtHandlerEnabled = uncaughtHandlerEnabled;
    }

    public Stacktrace getStacktrace() {
        return stacktrace;
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public Async getAsync() {
        return async;
    }

    public static class Stacktrace {

        /**
         * Some frames are replaced by the ... N more line as they are the same frames as in the enclosing exception.
         * <p>
         * Similar behaviour is enabled by default in Sentry.
         */
        private Boolean hideCommon;

        /**
         * Configure which package prefixes your application uses.
         */
        private Set<String> appPackages;

        public Boolean getHideCommon() {
            return hideCommon;
        }

        public void setHideCommon(Boolean hideCommon) {
            this.hideCommon = hideCommon;
        }

        public Set<String> getAppPackages() {
            return appPackages;
        }

        public void setAppPackages(Set<String> appPackages) {
            this.appPackages = appPackages;
        }

    }

    public static class Buffer {

        /**
         * Sentry can be configured to write events to a specified directory on disk anytime communication with the Sentry
         * server fails. If the directory doesn’t exist, Sentry will attempt to create it on startup and may therefore
         * need write permission on the parent directory. Sentry always requires write permission on the buffer
         * directory itself.
         */
        private String dir;

        /**
         * The maximum number of events that will be stored on disk defaults to 10.
         */
        private Integer size;

        /**
         * If a buffer directory is provided, a background thread will periodically attempt to re-send the events that
         * are found on disk. By default it will attempt to send events every 60 seconds.
         */
        private Integer flushTime;

        /**
         * In order to shutdown the buffer flushing thread gracefully, a ShutdownHook is created. By default, the buffer
         * flushing thread is given 1 second to shutdown gracefully.
         * The special value -1 can be used to disable the timeout and wait indefinitely for the executor to terminate.
         */
        private Integer shutdownTimeout;

        /**
         * The ShutdownHook could lead to memory leaks in an environment where the life cycle of Sentry doesn’t match
         * the life cycle of the JVM.
         * An example would be in a JEE environment where the application using Sentry could be deployed and undeployed
         * regularly.
         * To avoid this behaviour, it is possible to disable the graceful shutdown.
         */
        private Boolean gracefulShutdown;

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public Integer getSize() {
            return size;
        }

        public void setSize(Integer size) {
            this.size = size;
        }

        public Integer getFlushTime() {
            return flushTime;
        }

        public void setFlushTime(Integer flushTime) {
            this.flushTime = flushTime;
        }

        public Integer getShutdownTimeout() {
            return shutdownTimeout;
        }

        public void setShutdownTimeout(Integer shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }

        public Boolean getGracefulShutdown() {
            return gracefulShutdown;
        }

        public void setGracefulShutdown(Boolean gracefulShutdown) {
            this.gracefulShutdown = gracefulShutdown;
        }

    }

    public static class Async {

        /**
         * In order to avoid performance issues due to a large amount of logs being generated or a slow connection to
         * the Sentry server, an asynchronous connection is set up, using a low priority thread pool to submit events
         * to Sentry.
         */
        private Boolean enabled;

        /**
         * In order to shutdown the asynchronous connection gracefully, a ShutdownHook is created. By default, the
         * asynchronous connection is given 1 second to shutdown gracefully.
         * The special value -1 can be used to disable the timeout and wait indefinitely for the executor to terminate.
         */
        private Integer shutdownTimeout;

        /**
         * The ShutdownHook could lead to memory leaks in an environment where the life cycle of Sentry doesn’t match
         * the life cycle of the JVM.
         * An example would be in a JEE environment where the application using Sentry could be deployed and undeployed
         * regularly.
         * To avoid this behaviour, it is possible to disable the graceful shutdown. This might lead to some log entries
         * being lost if the log application doesn’t shut down the SentryClient instance nicely.
         */
        private Boolean gracefulShutdown;

        /**
         * The default queue used to store unprocessed events is limited to 50 items. Additional items added once the
         * queue is full are dropped and never sent to the Sentry server. Depending on the environment (if the memory
         * is sparse) it is important to be able to control the size of that queue to avoid memory issues.
         * This means that if the connection to the Sentry server is down, only the 100 most recent events will be
         * stored and processed as soon as the server is back up.
         * The special value -1 can be used to enable an unlimited queue. Beware that network connectivity or Sentry
         * server issues could mean your process will run out of memory.
         */
        private Integer queueSize;

        /**
         * By default the thread pool used by the async connection contains one thread per processor available to the JVM.
         * It’s possible to manually set the number of threads.
         */
        private Integer threads;

        /**
         * In most cases sending logs to Sentry isn’t as important as an application running smoothly, so the threads
         * have a minimal priority: https://docs.oracle.com/javase/6/docs/api/java/lang/Thread.html#MIN_PRIORITY.
         */
        private Integer priority;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getShutdownTimeout() {
            return shutdownTimeout;
        }

        public void setShutdownTimeout(Integer shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }

        public Boolean getGracefulShutdown() {
            return gracefulShutdown;
        }

        public void setGracefulShutdown(Boolean gracefulShutdown) {
            this.gracefulShutdown = gracefulShutdown;
        }

        public Integer getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(Integer queueSize) {
            this.queueSize = queueSize;
        }

        public Integer getThreads() {
            return threads;
        }

        public void setThreads(Integer threads) {
            this.threads = threads;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }

    }

}
