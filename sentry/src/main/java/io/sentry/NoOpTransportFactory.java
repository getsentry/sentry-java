package io.sentry;

import io.sentry.transport.ITransport;
import io.sentry.transport.NoOpTransport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpTransportFactory implements TransportFactory {

  private static final NoOpTransportFactory instance = new NoOpTransportFactory();

  public static NoOpTransportFactory getInstance() {
    return instance;
  }

  private NoOpTransportFactory() {}

  @Override
  public ITransport create(@NotNull SentryOptions options) {
    return NoOpTransport.getInstance();
  }
}
