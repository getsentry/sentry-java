package io.sentry.event.helper;

import javax.servlet.http.HttpServletRequest;

/**
 * The simplest (and default) {@link RemoteAddressResolver}.
 */
public class BasicRemoteAddressResolver implements RemoteAddressResolver {

    /**
     * Uses {@link HttpServletRequest#getRemoteAddr()} to resolve the REMOTE_ADDR.
     *
     * @param request HttpServletRequest
     * @return the IP address of the client or last proxy that sent the request.
     */
    public String getRemoteAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

}
