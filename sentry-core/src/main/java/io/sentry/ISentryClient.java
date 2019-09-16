package io.sentry;

import java.util.UUID;

public interface ISentryClient {
  boolean isEnabled();

  UUID captureEvent(SentryEvent event);

  void close();
}
