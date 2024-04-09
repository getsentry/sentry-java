package io.sentry.transport;

import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpTransport implements ITransport {

  private static final NoOpTransport instance = new NoOpTransport();

  public static @NotNull NoOpTransport getInstance() {
    return instance;
  }

  private NoOpTransport() {}

  @Override
  public void send(final @NotNull SentryEnvelope envelope, final @NotNull Hint hint)
      throws IOException {}

  @Override
  public void flush(long timeoutMillis) {}

  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return null;
  }

  @Override
  public void close() throws IOException {}

  @Override
  public void close(final boolean isRestarting) throws IOException {}
}
