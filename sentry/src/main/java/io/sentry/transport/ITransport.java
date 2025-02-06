package io.sentry.transport;

import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A transport is in charge of sending the event to the Sentry server. */
public interface ITransport extends Closeable {
  void send(@NotNull SentryEnvelope envelope, @NotNull Hint hint) throws IOException;

  default void send(@NotNull SentryEnvelope envelope) throws IOException {
    send(envelope, new Hint());
  }

  default boolean isHealthy() {
    return true;
  }

  /**
   * Flushes events queued up, but keeps the client enabled. Not implemented yet.
   *
   * @param timeoutMillis time in milliseconds
   */
  void flush(long timeoutMillis);

  @Nullable
  RateLimiter getRateLimiter();

  /**
   * Closes the transport.
   *
   * @param isRestarting if true, avoids locking the main thread by dropping the current connection.
   */
  void close(boolean isRestarting) throws IOException;
}
