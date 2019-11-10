package io.sentry.spring.autoconfigure;

import io.sentry.DefaultSentryClientFactory;
import io.sentry.config.provider.ConfigurationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpringBootConfigurationProvider implements ConfigurationProvider {

    private static final Logger logger = LoggerFactory.getLogger(SpringBootConfigurationProvider.class);

    private final SentryProperties sentryProperties;

    public SpringBootConfigurationProvider(SentryProperties sentryProperties) {
        this.sentryProperties = sentryProperties;
    }

    @Override
    public String getProperty(String key) {
        switch (key) {
            case DefaultSentryClientFactory.COMPRESSION_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getCompression());
            case DefaultSentryClientFactory.MAX_MESSAGE_LENGTH_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getMaxMessageLength());
            case DefaultSentryClientFactory.CONNECTION_TIMEOUT_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getTimeout());
            case DefaultSentryClientFactory.SAMPLE_RATE_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getSampleRate());
            case DefaultSentryClientFactory.UNCAUGHT_HANDLER_ENABLED_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getUncaughtHandlerEnabled());
            case DefaultSentryClientFactory.HIDE_COMMON_FRAMES_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getStacktrace().getHideCommon());
            case DefaultSentryClientFactory.IN_APP_FRAMES_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getStacktrace().getAppPackages());
            case DefaultSentryClientFactory.BUFFER_DIR_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getBuffer().getDir());
            case DefaultSentryClientFactory.BUFFER_SIZE_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getBuffer().getSize());
            case DefaultSentryClientFactory.BUFFER_FLUSHTIME_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getBuffer().getFlushTime());
            case DefaultSentryClientFactory.BUFFER_SHUTDOWN_TIMEOUT_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getBuffer().getShutdownTimeout());
            case DefaultSentryClientFactory.BUFFER_GRACEFUL_SHUTDOWN_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getBuffer().getGracefulShutdown());
            case DefaultSentryClientFactory.ASYNC_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getAsync().getEnabled());
            case DefaultSentryClientFactory.ASYNC_SHUTDOWN_TIMEOUT_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getAsync().getShutdownTimeout());
            case DefaultSentryClientFactory.ASYNC_GRACEFUL_SHUTDOWN_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getAsync().getGracefulShutdown());
            case DefaultSentryClientFactory.ASYNC_QUEUE_SIZE_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getAsync().getQueueSize());
            case DefaultSentryClientFactory.ASYNC_THREADS_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getAsync().getThreads());
            case DefaultSentryClientFactory.ASYNC_PRIORITY_OPTION:
                return logAndReturnIfSet(key, sentryProperties.getAsync().getPriority());
            default:
                logger.debug("Unsupported option: {}", key);
                return null;
        }
    }

    private String logAndReturnIfSet(String key, Object value) {
        if (value == null)
            return null;

        String ret = value.toString();

        logger.debug("Found {}={} in Spring Boot config.", key, ret);

        return ret;
    }

}
