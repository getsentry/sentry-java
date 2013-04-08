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

    public static Map<String, Collection<String>> extractHeaders(HttpServletRequest servletRequest) {
        Collection<String> headerNames = Collections.list(servletRequest.getHeaderNames());
        Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>(headerNames.size());
        for (String headerName : headerNames)
            headers.put(headerName, Collections.list(servletRequest.getHeaders(headerName)));
        return headers;
    }

    @Override
    public String getInterfaceName() {
        return HTTP_INTERFACE;
    }

    public HttpServletRequest getRequest() {
        return request;
    }
}
