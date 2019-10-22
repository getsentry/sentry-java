package io.sentry.core;

import io.sentry.core.transport.IConnectionConfigurator;
import java.net.HttpURLConnection;

/**
 * Used by {@link SentryClient} to inject credentials into the HTTP requests for sending the events.
 */
final class CredentialsSettingConfigurator implements IConnectionConfigurator {
  /** HTTP Header for the user agent. */
  private static final String USER_AGENT = "User-Agent";
  /** HTTP Header for the authentication to Sentry. */
  private static final String SENTRY_AUTH = "X-Sentry-Auth";

  private final String authHeader;
  private final String userAgent;

  CredentialsSettingConfigurator(Dsn dsn, String clientName) {

    String publicKey = dsn.getPublicKey();
    String secretKey = dsn.getSecretKey();

    this.authHeader =
        "Sentry sentry_version="
            + SentryClient.SENTRY_PROTOCOL_VERSION
            + ","
            + "sentry_client="
            + clientName
            + ","
            + "sentry_key="
            + publicKey
            + (secretKey != null && secretKey.length() > 0 ? (",sentry_secret=" + secretKey) : "");
    this.userAgent = clientName;
  }

  @Override
  public void configure(HttpURLConnection connection) {
    connection.setRequestProperty(USER_AGENT, userAgent);
    connection.setRequestProperty(SENTRY_AUTH, authHeader);
  }
}
