package io.sentry;

import io.sentry.transport.ITransport;
import org.jetbrains.annotations.NotNull;

/** Creates instances of {@link ITransport}. */
public interface TransportFactory {
  ITransport create(final @NotNull SentryOptions options);
}
