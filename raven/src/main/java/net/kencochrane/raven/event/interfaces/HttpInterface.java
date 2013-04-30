package net.kencochrane.raven.event.interfaces;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The HTTP interface for Sentry allows to add an HTTP request to an event.
 */
public class HttpInterface implements SentryInterface {
    public static final String HTTP_INTERFACE = "sentry.interfaces.Http";
    private final HttpServletRequest request;

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
