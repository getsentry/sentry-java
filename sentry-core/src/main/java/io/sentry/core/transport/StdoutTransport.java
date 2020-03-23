package io.sentry.core.transport;

import io.sentry.core.ISerializer;
import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEvent;
import io.sentry.core.util.Objects;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import org.jetbrains.annotations.NotNull;

public final class StdoutTransport implements ITransport {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull ISerializer serializer;

  public StdoutTransport(final @NotNull ISerializer serializer) {
    this.serializer = Objects.requireNonNull(serializer, "Serializer is required");
  }

  @Override
  public TransportResult send(final @NotNull SentryEvent event) throws IOException {
    Objects.requireNonNull(event, "SentryEvent is required");

    try (final Writer writer = new BufferedWriter(new OutputStreamWriter(System.out, UTF_8));
        final Writer printWriter = new PrintWriter(writer)) {
      serializer.serialize(event, printWriter);
      return TransportResult.success();
    }
  }

  @Override
  public boolean isRetryAfter(String type) {
    return false;
  }

  @Override
  public TransportResult send(final @NotNull SentryEnvelope envelope) throws IOException {
    Objects.requireNonNull(envelope, "SentryEnvelope is required");

    try (final Writer writer = new BufferedWriter(new OutputStreamWriter(System.out, UTF_8));
        final Writer printWriter = new PrintWriter(writer)) {
      serializer.serialize(envelope, printWriter);
      return TransportResult.success();
    } catch (Exception e) {
      return TransportResult.error(-1);
    }
  }

  @Override
  public void close() {}
}
