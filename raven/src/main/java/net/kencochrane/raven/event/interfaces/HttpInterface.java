package net.kencochrane.raven.event.interfaces;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * The HTTP interface for Sentry allows to add an HTTP request to an event.
 */
public class HttpInterface implements SentryInterface {
    /**
     * Name of the HTTP interface in Sentry.
     */
    public static final String HTTP_INTERFACE = "sentry.interfaces.Http";
    private final String requestUrl;
    private final String method;
    private final Map<String, Collection<String>> parameters;
    private final String queryString;
    private final Map<String, String> cookies;
    private final String remoteAddr;
    private final String serverName;
    private final int serverPort;
    private final String localAddr;
    private final String localName;
    private final int localPort;
    private final String protocol;
    private final boolean secure;
    private final boolean asyncStarted;
    private final String authType;
    private final String remoteUser;
    private final Map<String, Collection<String>> headers;

    /**
     * Creates a an HTTP element for an {@link net.kencochrane.raven.event.Event}.
     *
     * @param request Captured HTTP request to send to Sentry.
     */
    public HttpInterface(HttpServletRequest request) {
        this.requestUrl = request.getRequestURL().toString();
        this.method = request.getMethod();
        this.parameters = new HashMap<String, Collection<String>>();
        for (Map.Entry<String, String[]> parameterMapEntry : request.getParameterMap().entrySet())
            this.parameters.put(parameterMapEntry.getKey(), Arrays.asList(parameterMapEntry.getValue()));
        this.queryString = request.getQueryString();
        if (request.getCookies() != null) {
            this.cookies = new HashMap<String, String>();
            for (Cookie cookie : request.getCookies())
                this.cookies.put(cookie.getName(), cookie.getValue());
        } else {
            this.cookies = Collections.emptyMap();
        }
        this.remoteAddr = request.getRemoteAddr();
        this.serverName = request.getServerName();
        this.serverPort = request.getServerPort();
        this.localAddr = request.getLocalAddr();
        this.localName = request.getLocalName();
        this.localPort = request.getLocalPort();
        this.protocol = request.getProtocol();
        this.secure = request.isSecure();
        this.asyncStarted = request.isAsyncStarted();
        this.authType = request.getAuthType();
        this.remoteUser = request.getRemoteUser();
        this.headers = new HashMap<String, Collection<String>>();
        for (String headerName : Collections.list(request.getHeaderNames()))
            this.headers.put(headerName, Collections.list(request.getHeaders(headerName)));
    }

    @Override
    public String getInterfaceName() {
        return HTTP_INTERFACE;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, Collection<String>> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    public String getQueryString() {
        return queryString;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public String getServerName() {
        return serverName;
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getLocalAddr() {
        return localAddr;
    }

    public String getLocalName() {
        return localName;
    }

    public int getLocalPort() {
        return localPort;
    }

    public String getProtocol() {
        return protocol;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean isAsyncStarted() {
        return asyncStarted;
    }

    public String getAuthType() {
        return authType;
    }

    public String getRemoteUser() {
        return remoteUser;
    }

    public Map<String, Collection<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }
}
