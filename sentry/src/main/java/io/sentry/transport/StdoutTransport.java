package io.sentry.transport;

import io.sentry.ISerializer;
import io.sentry.SentryEnvelope;
import io.sentry.util.Objects;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public final class StdoutTransport implements ITransport {

  private final @NotNull ISerializer serializer;

  public StdoutTransport(final @NotNull ISerializer serializer) {
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required");
  }

  @Override
  public boolean isRetryAfter(String type) {
    return false;
  }

  @Override
  public TransportResult send(final @NotNull SentryEnvelope envelope) throws IOException {
    Objects.requireNonNull(envelope, "SentryEnvelope is required");

    try {
      serializer.serialize(envelope, System.out);
      return TransportResult.success();
    } catch (Exception e) {
      return TransportResult.error();
    }
  }

  @Override
  public void close() {}
}
