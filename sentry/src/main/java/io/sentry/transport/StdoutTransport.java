package io.sentry.transport;

import io.sentry.ISerializer;
import io.sentry.SentryEnvelope;
import io.sentry.util.Objects;
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
      return TransportResult.error();
    }
  }

  @Override
  public void close() {}
}
