package net.kencochrane.raven.servlet;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

/**
 * Request listener in charge of capturing {@link HttpServletRequest} to allow
 * {@link net.kencochrane.raven.event.helper.HttpEventBuilderHelper} to provide details on the current HTTP session
 * in the event sent to Sentry.
 */
//TODO: Consider Servlet < 3?
@WebListener
public class RavenServletRequestListener implements ServletRequestListener {
    private static final ThreadLocal<HttpServletRequest> THREAD_REQUEST = new ThreadLocal<HttpServletRequest>();

    public static HttpServletRequest getServletRequest() {
        return THREAD_REQUEST.get();
    }

    @Override
    public void requestDestroyed(ServletRequestEvent servletRequestEvent) {
        THREAD_REQUEST.remove();
    }

    @Override
    public void requestInitialized(ServletRequestEvent servletRequestEvent) {
        ServletRequest servletRequest = servletRequestEvent.getServletRequest();
        if (servletRequest instanceof HttpServletRequest)
            THREAD_REQUEST.set((HttpServletRequest) servletRequest);
    }
}
