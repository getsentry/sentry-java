package io.sentry.transport;

import io.sentry.Hint;
import io.sentry.ISerializer;
import io.sentry.SentryEnvelope;
import io.sentry.util.ExceptionUtils;
import io.sentry.util.Objects;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StdoutTransport implements ITransport {

  private final @NotNull ISerializer serializer;

  public StdoutTransport(final @NotNull ISerializer serializer) {
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required");
  }

  @Override
  public void send(final @NotNull SentryEnvelope envelope, final @NotNull Hint hint)
      throws IOException {
    Objects.requireNonNull(envelope, "SentryEnvelope is required");

    try {
      serializer.serialize(envelope, System.out);
    } catch (Throwable e) {
      // Serializing runs the items' data factories, e.g. an attachment's byte provider, so we
      // can't know what this throws. The envelope may be printed only partially.
      // TODO: debug log so we be aware serialization failed here
      ExceptionUtils.rethrowIfFatal(e);
    }
  }

  @Override
  public void flush(long timeoutMillis) {
    System.out.println("Flushing");
  }

  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return null;
  }

  @Override
  public void close() {}

  @Override
  public void close(final boolean isRestarting) {}
}
