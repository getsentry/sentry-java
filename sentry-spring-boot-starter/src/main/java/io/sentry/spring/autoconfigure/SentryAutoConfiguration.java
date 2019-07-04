package io.sentry.spring.autoconfigure;

import io.sentry.spring.SentryExceptionResolver;
import io.sentry.spring.SentryServletContextInitializer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spring Auto Configuration for Sentry.
 */
@Configuration
@ConditionalOnClass({ HandlerExceptionResolver.class, SentryExceptionResolver.class })
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "sentry.enabled", havingValue = "true", matchIfMissing = true)
public class SentryAutoConfiguration {

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
