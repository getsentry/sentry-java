package io.sentry;

import io.sentry.transport.ITransport;
import org.jetbrains.annotations.NotNull;

/** Creates instances of {@link ITransport}. */
public interface ITransportFactory {
  ITransport create(final @NotNull SentryOptions options);
}
