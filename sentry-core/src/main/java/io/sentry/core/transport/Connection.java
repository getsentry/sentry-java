package io.sentry.core.transport;

import io.sentry.core.SentryEvent;
import java.io.IOException;

public interface Connection {
  void send(SentryEvent event) throws IOException;

  void close() throws IOException;
}
