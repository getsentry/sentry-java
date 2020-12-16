package io.sentry.transport;

import io.sentry.SentryEnvelope;
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
  public void send(SentryEnvelope envelope, Object hint) throws IOException {}

  @Override
  public void close() throws IOException {}
}
