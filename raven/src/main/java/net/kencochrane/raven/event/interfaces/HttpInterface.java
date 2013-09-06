package net.kencochrane.raven.event.interfaces;

import javax.servlet.http.HttpServletRequest;

/**
 * The HTTP interface for Sentry allows to add an HTTP request to an event.
 */
public class HttpInterface implements SentryInterface {
    /**
     * Name of the HTTP interface in Sentry.
     */
    public static final String HTTP_INTERFACE = "sentry.interfaces.Http";
    private final HttpServletRequest request;

    /**
     * Creates a an HTTP element for an {@link net.kencochrane.raven.event.Event}.
     *
     * @param request Catpured HTTP request to send to Sentry.
     */
    public HttpInterface(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String getInterfaceName() {
        return HTTP_INTERFACE;
    }

    public HttpServletRequest getRequest() {
        return request;
    }
}
