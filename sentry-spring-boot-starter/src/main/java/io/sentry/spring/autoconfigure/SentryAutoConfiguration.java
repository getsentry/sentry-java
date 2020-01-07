package io.sentry.spring.autoconfigure;

import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.SentryOptions;
import io.sentry.config.Lookup;
import io.sentry.config.provider.ConfigurationProvider;
import io.sentry.connection.EventSendCallback;
import io.sentry.event.helper.EventBuilderHelper;
import io.sentry.event.helper.ShouldSendEventCallback;
import io.sentry.spring.SentryExceptionResolver;
import io.sentry.spring.SentryServletContextInitializer;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Spring Auto Configuration for Sentry.
 */
@Configuration
@ConditionalOnClass({HandlerExceptionResolver.class, SentryExceptionResolver.class})
@EnableConfigurationProperties(SentryProperties.class)
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "sentry.enabled", havingValue = "true", matchIfMissing = true)
public class SentryAutoConfiguration {

    /**
     * Resolves a {@link HandlerExceptionResolver}.
     *
     * @return a new instance of {@link HandlerExceptionResolver}.
     */
    @Bean
    @ConditionalOnMissingBean(SentryExceptionResolver.class)
    public HandlerExceptionResolver sentryExceptionResolver() {
        return new SentryExceptionResolver();
    }

    /**
     * Initializes a {@link ServletContextInitializer}.
     *
     * @return a new instance of {@link SentryServletContextInitializer}.
     */
    @Bean
    @ConditionalOnMissingBean(SentryServletContextInitializer.class)
    public ServletContextInitializer sentryServletContextInitializer() {
        return new SentryServletContextInitializer();
    }

    /**
     * Initializes a {@link List<EventBuilderHelper>}.
     *
     * @return a new instance of {@link List<EventBuilderHelper>}.
     */
    @Bean
    @ConditionalOnMissingBean(EventBuilderHelper.class)
    public List<EventBuilderHelper> defaultEventBuilderHelpers() {
        return Collections.emptyList();
    }

    /**
     * Initializes a {@link List<EventSendCallback>}.
     *
     * @return a new instance of {@link List<EventSendCallback>}.
     */
    @Bean
    @ConditionalOnMissingBean(EventSendCallback.class)
    public List<EventSendCallback> defaultEventSendCallbacks() {
        return Collections.emptyList();
    }

    /**
     * Initializes a {@link List<ShouldSendEventCallback>}.
     *
     * @return a new instance of {@link List<ShouldSendEventCallback>}.
     */
    @Bean
    @ConditionalOnMissingBean(ShouldSendEventCallback.class)
    public List<ShouldSendEventCallback> defaultShouldSendEventCallbacks() {
        return Collections.emptyList();
    }

    /**
     * Initializes a {@link SentryClient}.
     *
     * @return a new instance of {@link SentryClient}.
     */
    @Bean
    @ConditionalOnMissingBean(SentryClient.class)
    @ConditionalOnProperty(name = "sentry.init-default-client", havingValue = "true", matchIfMissing = true)
    public SentryClient sentryClient(SentryProperties properties,
                                     List<EventBuilderHelper> eventBuilderHelpers,
                                     List<EventSendCallback> eventSendCallbacks,
                                     List<ShouldSendEventCallback> shouldSendEventCallbacks) {
        String dsn = properties.getDsn() != null ? properties.getDsn().toString() : null;

        SentryOptions sentryOptions = SentryOptions.from(createLookup(properties), dsn, null);

        SentryClient sentryClient = Sentry.init(sentryOptions);

        if (!StringUtils.isEmpty(properties.getRelease())) {
            sentryClient.setRelease(properties.getRelease());
        }

        if (!StringUtils.isEmpty(properties.getDist())) {
            sentryClient.setDist(properties.getDist());
        }

        if (!StringUtils.isEmpty(properties.getEnvironment())) {
            sentryClient.setEnvironment(properties.getEnvironment());
        }

        if (!StringUtils.isEmpty(properties.getServerName())) {
            sentryClient.setServerName(properties.getServerName());
        }

        if (properties.getTags() != null && !properties.getTags().isEmpty()) {
            for (Map.Entry<String, String> tag : properties.getTags().entrySet()) {
                sentryClient.addTag(tag.getKey(), tag.getValue());
            }
        }

        if (properties.getMdcTags() != null && !properties.getMdcTags().isEmpty()) {
            for (String mdcTag : properties.getMdcTags()) {
                sentryClient.addMdcTag(mdcTag);
            }
        }

        if (properties.getExtra() != null && !properties.getExtra().isEmpty()) {
            for (Map.Entry<String, Object> extra : properties.getExtra().entrySet()) {
                sentryClient.addExtra(extra.getKey(), extra.getValue());
            }
        }

        if (eventBuilderHelpers != null && !eventBuilderHelpers.isEmpty()) {
            for (EventBuilderHelper eventBuilderHelper : eventBuilderHelpers) {
                sentryClient.addBuilderHelper(eventBuilderHelper);
            }
        }

        if (eventSendCallbacks != null && !eventSendCallbacks.isEmpty()) {
            for (EventSendCallback eventSendCallback : eventSendCallbacks) {
                sentryClient.addEventSendCallback(eventSendCallback);
            }
        }

        if (shouldSendEventCallbacks != null && !shouldSendEventCallbacks.isEmpty()) {
            for (ShouldSendEventCallback shouldSendEventCallback : shouldSendEventCallbacks) {
                sentryClient.addShouldSendEventCallback(shouldSendEventCallback);
            }
        }

        return sentryClient;
    }

    private Lookup createLookup(SentryProperties properties) {
        return Lookup.getDefaultWithAdditionalProviders(
                Collections.<ConfigurationProvider>singletonList(new SpringBootConfigurationProvider(properties)),
                Collections.<ConfigurationProvider>emptyList()
        );
    }

}
