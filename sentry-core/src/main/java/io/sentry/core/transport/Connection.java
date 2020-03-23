package io.sentry.core.transport;

import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEvent;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

public interface Connection {
  void send(SentryEvent event, @Nullable Object hint) throws IOException;

  default void send(SentryEvent event) throws IOException {
    send(event, null);
  }

  void send(SentryEnvelope event, @Nullable Object hint) throws IOException;

  default void send(SentryEnvelope envelope) throws IOException {
    send(envelope, null);
  }

  void close() throws IOException;
}
