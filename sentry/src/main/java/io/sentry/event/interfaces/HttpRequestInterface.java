package io.sentry.event.interfaces;

import java.util.Enumeration;
import java.util.Map;


/**
 * Interface exposing http request methods.
 */
public interface HttpRequestInterface {

    /**
     * Gets the URL of the request.
     *
     * @return the request URL
     */
    StringBuffer getRequestURL();

    /**
     * Gets the query string.
     *
     * @return the query string
     */
    String getQueryString();

    /**
     * Gets the cookies.
     *
     * @return an array of {@link CookieInterface}
     */
    CookieInterface[] getCookies();

    /**
     * Gets the HTTP method.
     *
     * @return the HTTP method
     */
    String getMethod();

    /**
     * Gets the parameter map.
     *
     * @return the parameter map
     */
    Map<String, String[]> getParameterMap();

    /**
     * Gets the server name.
     *
     * @return the server name
     */
    String getServerName();

    /**
     * Gets the server port.
     *
     * @return the server port
     */
    int getServerPort();

    /**
     * Gets the local address.
     *
     * @return the local address
     */
    String getLocalAddr();

    /**
     * Gets the remote address.
     *
     * @return the remote address
     */
    String getRemoteAddr();

    /**
     * Gets the local name.
     *
     * @return the local name
     */
    String getLocalName();

    /**
     * Gets the local port.
     *
     * @return the local port
     */
    int getLocalPort();

    /**
     * Gets the protocol.
     *
     * @return the protocol
     */
    String getProtocol();

    /**
     * Returns a boolean indicating whether this request was made using a secure channel, such as HTTPS.
     *
     * @return a boolean indicating if the request was made using a secure channel
     */
    boolean isSecure();

    /**
     * Checks if this request has been put into asynchronous mode.
     *
     * @return true if this request has been put into asynchronous mode, false otherwise
     */
    boolean isAsyncStarted();

    /**
     * Returns the auth type.
     *
     * @return the auth type
     */
    String getAuthType();

    /**
     * Returns the remote user.
     *
     * @return the remote user
     */
    String getRemoteUser();

    /**
     * Returns the header names.
     *
     * @return the header name enumeration
     */
    Enumeration<String> getHeaderNames();

    /**
     * Returns the headers enumeration for a specific header name.
     *
     * @param name of the header
     * @return the headers for a specific key
     */
    Enumeration<String> getHeaders(String name);

    /**
     * Returns a header for a specific header name.
     *
     * @param name of the header
     * @return a header for a specific key
     */
    String getHeader(String name);

    /**
     * Returns the user {@link java.security.Principal}.
     *
     * @return the user principal
     */
    java.security.Principal getUserPrincipal();

}
