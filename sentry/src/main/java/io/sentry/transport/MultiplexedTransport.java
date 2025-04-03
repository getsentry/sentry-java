package io.sentry.transport;

import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import io.sentry.util.Objects;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MultiplexedTransport implements ITransport {
  private final @NotNull List<ITransport> transports;

  public MultiplexedTransport(final @NotNull List<ITransport> transports) {
    this.transports = Objects.requireNonNull(transports, "transports is required");
  }

  @Override
  public void send(final @NotNull SentryEnvelope envelope, final @NotNull Hint hint)
      throws IOException {
    for (ITransport transport : transports) {
      transport.send(envelope, hint);
    }
  }

  @Override
  public boolean isHealthy() {
    return transports.stream().allMatch(ITransport::isHealthy);
  }

  @Override
  public void flush(final long timeoutMillis) {
    transports.forEach(transport -> transport.flush(timeoutMillis));
  }

  @Override
  public @Nullable RateLimiter getRateLimiter() {
    // Prefer one with rate limit active, else fall back to arbitrary one
    final List<RateLimiter> rateLimiters =
        this.transports.stream().map(ITransport::getRateLimiter).collect(Collectors.toList());
    final Optional<RateLimiter> activeRateLimiter =
        rateLimiters.stream().filter(RateLimiter::isAnyRateLimitActive).findAny();
    return activeRateLimiter.orElse(rateLimiters.stream().findAny().orElse(null));
  }

  @Override
  public void close(final boolean isRestarting) throws IOException {
    for (ITransport transport : transports) {
      transport.close(isRestarting);
    }
  }

  @Override
  public void close() throws IOException {
    for (ITransport transport : transports) {
      transport.close();
    }
  }
}
