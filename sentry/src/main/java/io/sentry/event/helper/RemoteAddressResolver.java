package io.sentry.event.helper;


import io.sentry.event.interfaces.HttpRequestInterface;

/**
 * Interface that allows users to define how the REMOTE_ADDR
 * is set on each {@link io.sentry.event.Event}.
 */
public interface RemoteAddressResolver {

    /**
     * Returns the REMOTE_ADDR for the provided request.
     *
     * @param request HttpRequestInterface
     * @return String representing the desired REMOTE_ADDR.
     */
    String getRemoteAddress(HttpRequestInterface request);

}
