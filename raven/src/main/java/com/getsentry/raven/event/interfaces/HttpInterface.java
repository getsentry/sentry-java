package com.getsentry.raven.event.interfaces;

import com.getsentry.raven.event.helper.BasicRemoteAddressResolver;
import com.getsentry.raven.event.helper.RemoteAddressResolver;

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
     * This constructor is for compatibility reasons and should not be used.
     *
     * @param request HttpServletRequest
     */
    public HttpInterface(HttpServletRequest request) {
        this(request, new BasicRemoteAddressResolver());
    }

    /**
     * Creates a an HTTP element for an {@link com.getsentry.raven.event.Event}.
     *
     * @param request Captured HTTP request to send to Sentry.
     * @param remoteAddressResolver RemoteAddressResolver
     */
    public HttpInterface(HttpServletRequest request, RemoteAddressResolver remoteAddressResolver) {
        this.requestUrl = request.getRequestURL().toString();
        this.method = request.getMethod();
        this.parameters = new HashMap<>();
        for (Map.Entry<String, String[]> parameterMapEntry : request.getParameterMap().entrySet()) {
            this.parameters.put(parameterMapEntry.getKey(), Arrays.asList(parameterMapEntry.getValue()));
        }
        this.queryString = request.getQueryString();
        if (request.getCookies() != null) {
            this.cookies = new HashMap<>();
            for (Cookie cookie : request.getCookies()) {
                this.cookies.put(cookie.getName(), cookie.getValue());
            }
        } else {
            this.cookies = Collections.emptyMap();
        }
        this.remoteAddr = remoteAddressResolver.getRemoteAddress(request);
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
        this.headers = new HashMap<>();
        for (String headerName : Collections.list(request.getHeaderNames())) {
            this.headers.put(headerName, Collections.list(request.getHeaders(headerName)));
        }
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

    @Override
    public String toString() {
        return "HttpInterface{"
            + "requestUrl='" + requestUrl + '\''
            + ", method='" + method + '\''
            + ", queryString='" + queryString + '\''
            + ", parameters=" + parameters
            + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        HttpInterface that = (HttpInterface) o;

        if (asyncStarted != that.asyncStarted) {
            return false;
        }
        if (localPort != that.localPort) {
            return false;
        }
        if (secure != that.secure) {
            return false;
        }
        if (serverPort != that.serverPort) {
            return false;
        }
        if (authType != null ? !authType.equals(that.authType) : that.authType != null) {
            return false;
        }
        if (!cookies.equals(that.cookies)) {
            return false;
        }
        if (!headers.equals(that.headers)) {
            return false;
        }
        if (localAddr != null ? !localAddr.equals(that.localAddr) : that.localAddr != null) {
            return false;
        }
        if (localName != null ? !localName.equals(that.localName) : that.localName != null) {
            return false;
        }
        if (method != null ? !method.equals(that.method) : that.method != null) {
            return false;
        }
        if (!parameters.equals(that.parameters)) {
            return false;
        }
        if (protocol != null ? !protocol.equals(that.protocol) : that.protocol != null) {
            return false;
        }
        if (queryString != null ? !queryString.equals(that.queryString) : that.queryString != null) {
            return false;
        }
        if (remoteAddr != null ? !remoteAddr.equals(that.remoteAddr) : that.remoteAddr != null) {
            return false;
        }
        if (remoteUser != null ? !remoteUser.equals(that.remoteUser) : that.remoteUser != null) {
            return false;
        }
        if (!requestUrl.equals(that.requestUrl)) {
            return false;
        }
        if (serverName != null ? !serverName.equals(that.serverName) : that.serverName != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = requestUrl.hashCode();
        result = 31 * result + (method != null ? method.hashCode() : 0);
        result = 31 * result + parameters.hashCode();
        return result;
    }
}
