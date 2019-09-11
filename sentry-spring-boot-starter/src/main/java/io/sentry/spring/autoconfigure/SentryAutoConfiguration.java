package io.sentry.spring.autoconfigure;

import io.sentry.Sentry;
import io.sentry.connection.EventSendCallback;
import io.sentry.event.helper.ShouldSendEventCallback;
import io.sentry.spring.SentryExceptionResolver;
import io.sentry.spring.SentryServletContextInitializer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * Spring Auto Configuration for Sentry.
 */
@Configuration
@ConditionalOnClass({ HandlerExceptionResolver.class, SentryExceptionResolver.class })
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "sentry.enabled", havingValue = "true", matchIfMissing = true)
public class SentryAutoConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        Map<String, ShouldSendEventCallback> shouldSendEventCallbackMap = this.applicationContext.getBeansOfType(ShouldSendEventCallback.class);
        for (ShouldSendEventCallback shouldSendEventCallback : shouldSendEventCallbackMap.values()) {
            Sentry.getStoredClient().addShouldSendEventCallback(shouldSendEventCallback);
        }
        Map<String, EventSendCallback> eventSendCallbackMap = this.applicationContext.getBeansOfType(EventSendCallback.class);
        for (EventSendCallback eventSendCallback : eventSendCallbackMap.values()) {
            Sentry.getStoredClient().addEventSendCallback(eventSendCallback);
        }
    }

    /**
     * Resolves a {@link HandlerExceptionResolver}.
     * @return a new instance of {@link SentryAutoConfiguration}.
     */
    @Bean
    @ConditionalOnMissingBean(SentryExceptionResolver.class)
    public HandlerExceptionResolver sentryExceptionResolver() {
        return new SentryExceptionResolver();
    }

    /**
     * Initializes a {@link ServletContextInitializer}.
     * @return a new instance of {@link SentryServletContextInitializer}.
     */
    @Bean
    @ConditionalOnMissingBean(SentryServletContextInitializer.class)
    public ServletContextInitializer sentryServletContextInitializer() {
        return new SentryServletContextInitializer();
    }

}
