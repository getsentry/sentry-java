package net.kencochrane.raven.servlet;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

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
        if (servletRequestEvent instanceof HttpServletRequest)
            THREAD_REQUEST.set((HttpServletRequest) servletRequestEvent.getServletRequest());
    }
}
