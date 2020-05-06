package io.sentry.event.interfaces;

/**
 * Simple interface to abstract cookie name/value retrieval.
 */
public interface CookieInterface {

    /**
     * Returns the cookie name.
     *
     * @return the cookie name
     */
    String getName();

    /**
     * Returns the cookie value.
     *
     * @return the cookie value
     */
    String getValue();

}
