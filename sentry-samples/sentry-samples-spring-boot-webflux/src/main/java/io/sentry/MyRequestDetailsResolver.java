package io.sentry;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

// this is in package io.sentry so we can access DSN and not have to copy
public class MyRequestDetailsResolver {
  /** HTTP Header for the user agent. */
  private static final String USER_AGENT = "User-Agent";
  /** HTTP Header for the authentication to Sentry. */
  private static final String SENTRY_AUTH = "X-Sentry-Auth";

  @NotNull
  public static RequestDetails resolve(
      final @NotNull String dsnString, final @NotNull String sentryClientName) {
    final Dsn dsn = new Dsn(dsnString);
    final URI sentryUri = dsn.getSentryUri();
    final String envelopeUrl = sentryUri.resolve(sentryUri.getPath() + "/envelope/").toString();

    final String publicKey = dsn.getPublicKey();
    final String secretKey = dsn.getSecretKey();

    final String authHeader =
        "Sentry sentry_version="
            + SentryClient.SENTRY_PROTOCOL_VERSION
            + ","
            + "sentry_client="
            + sentryClientName
            + ","
            + "sentry_key="
            + publicKey
            + (secretKey != null && secretKey.length() > 0 ? (",sentry_secret=" + secretKey) : "");
    final String userAgent = sentryClientName;

    final Map<String, String> headers = new HashMap<>();
    headers.put(USER_AGENT, userAgent);
    headers.put(SENTRY_AUTH, authHeader);

    return new RequestDetails(envelopeUrl, headers);
  }
}
