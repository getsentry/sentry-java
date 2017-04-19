package io.sentry.servlet;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.Set;

/**
 * Servlet container initializer used to add the {@link SentryServletRequestListener} to the {@link ServletContext}.
 */
public class SentryServletContainerInitializer implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
        ctx.addListener(SentryServletRequestListener.class);
    }
}
