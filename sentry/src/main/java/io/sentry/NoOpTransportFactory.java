package io.sentry;

import io.sentry.transport.ITransport;
import io.sentry.transport.NoOpTransport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpTransportFactory implements ITransportFactory {

  private static final NoOpTransportFactory instance = new NoOpTransportFactory();

  public static NoOpTransportFactory getInstance() {
    return instance;
  }

  private NoOpTransportFactory() {}

  @Override
  public @NotNull ITransport create(
      final @NotNull SentryOptions options, final @NotNull RequestDetails requestDetails) {
    return NoOpTransport.getInstance();
  }
}
