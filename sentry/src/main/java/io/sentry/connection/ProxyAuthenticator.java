package io.sentry.connection;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * Proxy authenticator.
 */
public class ProxyAuthenticator extends Authenticator {
    private String user;
    private String pass;

    /**
     * Proxy authenticator.
     *
     * @param user proxy username
     * @param pass proxy password
     */
    public ProxyAuthenticator(String user, String pass) {
        this.user = user;
        this.pass = pass;
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        if (getRequestorType() == RequestorType.PROXY) {
            return new PasswordAuthentication(user, pass.toCharArray());
        }
        return null;
    }
}
