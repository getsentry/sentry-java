package io.sentry.spring;

import io.sentry.servlet.SentryServletRequestListener;
import org.springframework.boot.web.servlet.ServletContextInitializer;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/**
 * {@link ServletContextInitializer} implementation that enables data collection for
 * Sentry's {@link io.sentry.event.helper.HttpEventBuilderHelper}. Spring Boot
 * doesn't automatically activate {@link javax.servlet.ServletContainerInitializer}s,
 * so this exists so that it can be manually configured as a
 * {@link org.springframework.context.annotation.Bean} in the user's application.
 */
public class SentryServletContextInitializer implements ServletContextInitializer {
    @Override
    public void onStartup(ServletContext ctx) throws ServletException {
        ctx.addListener(SentryServletRequestListener.class);
    }
}
