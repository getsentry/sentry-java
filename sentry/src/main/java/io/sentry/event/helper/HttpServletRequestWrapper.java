package io.sentry.event.helper;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import io.sentry.event.interfaces.CookieInterface;
import io.sentry.event.interfaces.HttpRequestInterface;

/**
 * Simply wraps a {@link HttpServletRequest} in a {@link HttpRequestInterface}.
 */
public class HttpServletRequestWrapper implements HttpRequestInterface {

    private final HttpServletRequest request;

    /**
     * Constructor for wrapping a {@link HttpServletRequest}.
     *
     * @param request the request to wrap.
     */
    public HttpServletRequestWrapper(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public StringBuffer getRequestURL() {
        return request.getRequestURL();
    }

    @Override
    public String getQueryString() {
        return request.getQueryString();
    }

    @Override
    public CookieInterface[] getCookies() {

        List<CookieInterface> cookies = new ArrayList<>();

        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            cookies.add(new HttpServletCookieWrapper(cookie));

        }
        return cookies.toArray(new CookieInterface[cookies.size()]);
    }

    @Override
    public String getMethod() {
        return request.getMethod();
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return request.getParameterMap();
    }

    @Override
    public String getServerName() {
        return request.getServerName();
    }

    @Override
    public int getServerPort() {
        return request.getServerPort();
    }

    @Override
    public String getLocalAddr() {
        return request.getLocalAddr();
    }

    @Override
    public String getRemoteAddr() {
        return request.getRemoteAddr();
    }

    @Override
    public String getLocalName() {
        return request.getLocalName();
    }

    @Override
    public int getLocalPort() {
        return request.getLocalPort();
    }

    @Override
    public String getProtocol() {
        return request.getProtocol();
    }

    @Override
    public boolean isSecure() {
        return request.isSecure();
    }

    @Override
    public boolean isAsyncStarted() {
        return request.isAsyncStarted();
    }

    @Override
    public String getAuthType() {
        return request.getAuthType();
    }

    @Override
    public String getRemoteUser() {
        return request.getRemoteUser();
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return request.getHeaderNames();
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return request.getHeaders(name);
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public Principal getUserPrincipal() {
        return request.getUserPrincipal();
    }
}
