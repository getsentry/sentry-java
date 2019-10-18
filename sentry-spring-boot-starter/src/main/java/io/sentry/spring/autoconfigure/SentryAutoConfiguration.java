package io.sentry.spring.autoconfigure;

import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.connection.EventSendCallback;
import io.sentry.event.helper.EventBuilderHelper;
import io.sentry.event.helper.ShouldSendEventCallback;
import io.sentry.spring.SentryExceptionResolver;
import io.sentry.spring.SentryServletContextInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;

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
     * Initializes a {@link SentryClient}.
     *
     * @return a new instance of {@link SentryClient}.
     */
    @Bean
    @ConditionalOnMissingBean(SentryClient.class)
    @ConditionalOnProperty(name = "sentry.init-default-client", havingValue = "true", matchIfMissing = true)
    public SentryClient sentryClient(SentryProperties properties,
                                     @Autowired(required = false) List<EventBuilderHelper> eventBuilderHelpers,
                                     @Autowired(required = false) List<EventSendCallback> eventSendCallbacks,
                                     @Autowired(required = false) List<ShouldSendEventCallback> shouldSendEventCallbacks) {
        if (properties.getOptions() != null && !properties.getOptions().isEmpty()) {
            for (Map.Entry<String, String> option : properties.getOptions().entrySet()) {
                System.setProperty("sentry." + option.getKey(), option.getValue());
            }
        }

        SentryClient sentryClient = Sentry.init(properties.getDsn());

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

}
