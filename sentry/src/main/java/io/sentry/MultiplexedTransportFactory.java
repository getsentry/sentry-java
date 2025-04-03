package io.sentry;

import io.sentry.transport.ITransport;
import io.sentry.transport.MultiplexedTransport;
import io.sentry.util.Objects;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class MultiplexedTransportFactory implements ITransportFactory {

  private final @NotNull ITransportFactory transportFactory;
  private final @NotNull List<Dsn> dsns;

  public MultiplexedTransportFactory(
      final @NotNull ITransportFactory transportFactory, final @NotNull List<String> dsns) {
    Objects.requireNonNull(transportFactory, "transportFactory is required");
    Objects.requireNonNull(dsns, "dsns is required");

    this.transportFactory = transportFactory;
    this.dsns = dsns.stream().map(Dsn::new).collect(Collectors.toList());
  }

  @Override
  public @NotNull ITransport create(
      final @NotNull SentryOptions options, final @NotNull RequestDetails requestDetails) {
    final List<ITransport> transports =
        dsns.stream().map(dsn -> createTransport(options, dsn)).collect(Collectors.toList());
    return new MultiplexedTransport(transports);
  }

  private @NotNull ITransport createTransport(
      final @NotNull SentryOptions options, final @NotNull Dsn dsn) {
    final RequestDetails requestDetails =
        new RequestDetailsResolver(dsn, options.getSentryClientName()).resolve();
    return transportFactory.create(options, requestDetails);
  }
}
