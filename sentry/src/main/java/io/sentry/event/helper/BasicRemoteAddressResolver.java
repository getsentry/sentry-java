package io.sentry.event.helper;

import javax.servlet.http.HttpServletRequest;

import io.sentry.event.interfaces.HttpRequestInterface;

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
    public String getRemoteAddress(HttpRequestInterface request) {
        return request.getRemoteAddr();
    }

}
