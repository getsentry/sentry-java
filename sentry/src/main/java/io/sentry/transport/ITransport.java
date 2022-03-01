package io.sentry.transport;

import io.sentry.SentryEnvelope;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A transport is in charge of sending the event to the Sentry server. */
public interface ITransport extends Closeable {
  void send(@NotNull SentryEnvelope envelope, @Nullable Map<String, Object> hint)
      throws IOException;

  default void send(@NotNull SentryEnvelope envelope) throws IOException {
    send(envelope, null);
  }

  /**
   * Flushes events queued up, but keeps the client enabled. Not implemented yet.
   *
   * @param timeoutMillis time in milliseconds
   */
  void flush(long timeoutMillis);
}
