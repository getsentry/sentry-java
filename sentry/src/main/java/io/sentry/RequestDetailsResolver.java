package io.sentry;

import io.sentry.util.Objects;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Resolves {@link RequestDetails}. */
final class RequestDetailsResolver {
  /** HTTP Header for the user agent. */
  private static final String USER_AGENT = "User-Agent";
  /** HTTP Header for the authentication to Sentry. */
  private static final String SENTRY_AUTH = "X-Sentry-Auth";

  private final @NotNull SentryOptions options;

  public RequestDetailsResolver(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options is required");
  }

  @NotNull
  RequestDetails resolve() {
    final Dsn dsn = options.getParsedDsn();
    final URI sentryUri = dsn.getSentryUri();
    final String envelopeUrl = sentryUri.resolve(sentryUri.getPath() + "/envelope/").toString();

    final String publicKey = dsn.getPublicKey();
    final String secretKey = dsn.getSecretKey();

    final String authHeader =
        "Sentry sentry_version="
            + SentryClient.SENTRY_PROTOCOL_VERSION
            + ","
            + "sentry_client="
            + options.getSentryClientName()
            + ","
            + "sentry_key="
            + publicKey
            + (secretKey != null && secretKey.length() > 0 ? (",sentry_secret=" + secretKey) : "");
    final String userAgent = options.getSentryClientName();

    final Map<String, String> headers = new HashMap<>();
    headers.put(USER_AGENT, userAgent);
    headers.put(SENTRY_AUTH, authHeader);

    return new RequestDetails(envelopeUrl, headers);
  }
}
