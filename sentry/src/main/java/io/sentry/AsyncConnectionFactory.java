package io.sentry;

import io.sentry.cache.IEnvelopeCache;
import io.sentry.transport.AsyncConnection;

final class AsyncConnectionFactory {
  private AsyncConnectionFactory() {}

  public static AsyncConnection create(SentryOptions options, IEnvelopeCache envelopeCache) {

    // the connection doesn't do any retries of failed sends and can hold at most the same number
    // of pending events as there are being cached. The rest is dropped.
    return new AsyncConnection(
        options.getTransport(),
        options.getTransportGate(),
        envelopeCache,
        options.getMaxQueueSize(),
        options);
  }
}
