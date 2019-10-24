package io.sentry.spring.autoconfigure;

import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.SentryOptions;
import io.sentry.config.ContextClassLoaderResourceLoader;
import io.sentry.config.Lookup;
import io.sentry.config.location.CompoundResourceLocator;
import io.sentry.config.location.ConfigurationResourceLocator;
import io.sentry.config.location.EnvironmentBasedLocator;
import io.sentry.config.location.SystemPropertiesBasedLocator;
import io.sentry.config.provider.CompoundConfigurationProvider;
import io.sentry.config.provider.ConfigurationProvider;
import io.sentry.config.provider.EnvironmentConfigurationProvider;
import io.sentry.config.provider.LocatorBasedConfigurationProvider;
import io.sentry.config.provider.SystemPropertiesConfigurationProvider;
import io.sentry.connection.EventSendCallback;
import io.sentry.event.helper.EventBuilderHelper;
import io.sentry.event.helper.ShouldSendEventCallback;
import io.sentry.spring.SentryExceptionResolver;
import io.sentry.spring.SentryServletContextInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Spring Auto Configuration for Sentry.
 */
@Configuration
@ConditionalOnClass({HandlerExceptionResolver.class, SentryExceptionResolver.class})
@EnableConfigurationProperties(SentryProperties.class)
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "sentry.enabled", havingValue = "true", matchIfMissing = true)
public class SentryAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(Lookup.class);

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
        SentryOptions sentryOptions = SentryOptions.from(createLookup(properties), properties.getDsn(), null);

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
        return new Lookup(
                new CompoundConfigurationProvider(getDefaultHighPriorityConfigurationProviders(properties)),
                new CompoundConfigurationProvider(getDefaultLowPriorityConfigurationProviders())
        );
    }

    private List<ConfigurationProvider> getDefaultHighPriorityConfigurationProviders(SentryProperties properties) {
        return asList(
                new SpringBootConfigurationProvider(properties),
                new SystemPropertiesConfigurationProvider(),
                new EnvironmentConfigurationProvider()
        );
    }

    private List<ConfigurationProvider> getDefaultLowPriorityConfigurationProviders() {
        try {
            ConfigurationProvider configurationProvider = new LocatorBasedConfigurationProvider(
                    new ContextClassLoaderResourceLoader(),
                    new CompoundResourceLocator(getDefaultResourceLocators()),
                    Charset.defaultCharset()
            );

            return singletonList(configurationProvider);
        } catch (IOException e) {
            logger.debug("Failed to instantiate resource locator-based configuration provider.", e);
            return emptyList();
        }
    }

    private List<ConfigurationResourceLocator> getDefaultResourceLocators() {
        return asList(
                new SystemPropertiesBasedLocator(),
                new EnvironmentBasedLocator()
        );
    }

}
