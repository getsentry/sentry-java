package com.getsentry.raven.servlet;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.Set;

/**
 * Servlet container initializer used to add the {@link RavenServletRequestListener} to the {@link ServletContext}.
 */
public class RavenServletContainerInitializer implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
        ctx.addListener(RavenServletRequestListener.class);
    }
}
