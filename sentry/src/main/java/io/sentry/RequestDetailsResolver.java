package io.sentry;

import io.sentry.util.Objects;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Resolves {@link RequestDetails}. */
final class RequestDetailsResolver {
  /** HTTP Header for the user agent. */
  private static final String USER_AGENT = "User-Agent";
  /** HTTP Header for the authentication to Sentry. */
  private static final String SENTRY_AUTH = "X-Sentry-Auth";

  private final @NotNull Dsn dsn;
  private final @Nullable String sentryClientName;

  public RequestDetailsResolver(final @NotNull Dsn dsn, final @Nullable String sentryClientName) {
    this.dsn = Objects.requireNonNull(dsn, "dsn is required");
    this.sentryClientName = sentryClientName;
  }

  public RequestDetailsResolver(final @NotNull SentryOptions options) {
    Objects.requireNonNull(options, "options is required");

    this.dsn = options.retrieveParsedDsn();
    this.sentryClientName = options.getSentryClientName();
  }

  @NotNull
  RequestDetails resolve() {
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

    final Map<String, String> headers = new HashMap<>();
    headers.put(USER_AGENT, sentryClientName);
    headers.put(SENTRY_AUTH, authHeader);

    return new RequestDetails(envelopeUrl, headers);
  }
}
