package io.sentry;

import io.sentry.transport.AsyncHttpTransport;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Creates {@link AsyncHttpTransport}. */
@ApiStatus.Internal
public final class AsyncHttpTransportFactory implements ITransportFactory {

  @Override
  public @NotNull ITransport create(
      final @NotNull SentryOptions options, final @NotNull RequestDetails requestDetails) {
    Objects.requireNonNull(options, "options is required");
    Objects.requireNonNull(requestDetails, "requestDetails is required");

    return new AsyncHttpTransport(
        options, new RateLimiter(options), options.getTransportGate(), requestDetails);
  }
}
