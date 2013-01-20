package net.kencochrane.raven.ext;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

/**
 * Store HttpServletRequest object in RavenMDC, allowing the request to be
 * accessed by {@link ServletJSONProcessor}.
 *
 * @author vvasabi
 * @since 1.0
 */
@WebListener
public class RavenServletRequestListener implements ServletRequestListener {

    private static final ThreadLocal<HttpServletRequest> THREAD_REQUEST
            = new ThreadLocal<HttpServletRequest>();

    @Override
    public void requestInitialized(ServletRequestEvent sre) {
        THREAD_REQUEST.set((HttpServletRequest)sre.getServletRequest());
    }

    @Override
    public void requestDestroyed(ServletRequestEvent sre) {
        THREAD_REQUEST.remove();
    }

    public static HttpServletRequest getRequest() {
        return THREAD_REQUEST.get();
    }

}
