package io.sentry.event.helper;

import javax.servlet.http.HttpServletRequest;

/**
 * Interface that allows users to define how the REMOTE_ADDR
 * is set on each {@link io.sentry.event.Event}.
 */
public interface RemoteAddressResolver {

    /**
     * Returns the REMOTE_ADDR for the provided request.
     *
     * @param request HttpServletRequest
     * @return String representing the desired REMOTE_ADDR.
     */
    String getRemoteAddress(HttpServletRequest request);

}
