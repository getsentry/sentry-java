package net.kencochrane.raven.ext;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;

import net.kencochrane.raven.spi.RavenMDC;

/**
 * Store HttpServletRequest object in RavenMDC, allowing the request to be
 * accessed by {@link ServletJSONProcessor}.
 *
 * @author vvasabi
 * @since 1.0
 */
@WebListener
public class RavenServletRequestListener implements ServletRequestListener {

    @Override
    public void requestInitialized(ServletRequestEvent sre) {
        RavenMDC mdc = RavenMDC.getInstance();
        mdc.put(ServletJSONProcessor.MDC_REQUEST, sre.getServletRequest());
    }

    @Override
    public void requestDestroyed(ServletRequestEvent sre) {
        RavenMDC mdc = RavenMDC.getInstance();
        mdc.remove(ServletJSONProcessor.MDC_REQUEST);
    }

}
