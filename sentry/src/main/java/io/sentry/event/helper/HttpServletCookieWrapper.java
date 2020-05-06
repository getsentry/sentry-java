package io.sentry.event.helper;

import javax.servlet.http.Cookie;

import io.sentry.event.interfaces.CookieInterface;

/**
 * Simply wraps a {@link Cookie} in a {@link CookieInterface}.
 */
public class HttpServletCookieWrapper implements CookieInterface {

    private final Cookie cookie;

    /**
     * Creates a new {@link HttpServletCookieWrapper} from a {@link Cookie}.
     *
     * @param cookie the cookie to wrap
     */
    public HttpServletCookieWrapper(Cookie cookie) {
        this.cookie = cookie;
    }

    @Override
    public String getName() {
        return cookie.getName();
    }

    @Override
    public String getValue() {
        return cookie.getValue();
    }
}
