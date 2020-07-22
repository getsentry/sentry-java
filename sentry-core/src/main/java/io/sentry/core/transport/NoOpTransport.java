package io.sentry.core.transport;

import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEvent;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NoOpTransport implements ITransport {

  private static final NoOpTransport instance = new NoOpTransport();

  public static NoOpTransport getInstance() {
    return instance;
  }

  private NoOpTransport() {}

  @Override
  public TransportResult send(SentryEvent event) throws IOException {
    return TransportResult.success();
  }

  @Override
  public boolean isRetryAfter(String type) {
    return false;
  }

  @Override
  public TransportResult send(SentryEnvelope envelope) throws IOException {
    return TransportResult.success();
  }

  @Override
  public void close() throws IOException {}
}
