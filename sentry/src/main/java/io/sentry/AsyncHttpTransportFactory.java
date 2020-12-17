package io.sentry;

import io.sentry.transport.AsyncHttpTransport;
import io.sentry.transport.IConnectionConfigurator;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;
import io.sentry.util.Objects;
import java.net.MalformedURLException;
import java.net.URL;
import org.jetbrains.annotations.NotNull;

final class AsyncHttpTransportFactory {

  private AsyncHttpTransportFactory() {}

  static ITransport create(final @NotNull SentryOptions options) {
    Objects.requireNonNull(options, "options is required");
    final Dsn parsedDsn = new Dsn(options.getDsn());
    final IConnectionConfigurator credentials =
        new CredentialsSettingConfigurator(parsedDsn, options.getSentryClientName());

    URL sentryUrl;
    try {
      sentryUrl = parsedDsn.getSentryUri().toURL();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Failed to compose the Sentry's server URL.", e);
    }

    return new AsyncHttpTransport(
        options,
        new RateLimiter(options.getLogger()),
        options.getTransportGate(),
        credentials,
        sentryUrl);
  }
}
