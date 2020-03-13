package io.sentry.core;

import io.sentry.core.cache.IEventCache;
import io.sentry.core.cache.ISessionCache;
import io.sentry.core.transport.AsyncConnection;

final class AsyncConnectionFactory {
  private AsyncConnectionFactory() {}

  public static AsyncConnection create(
      SentryOptions options, IEventCache eventCache, ISessionCache sessionCache) {

    // the connection doesn't do any retries of failed sends and can hold at most the same number
    // of pending events as there are being cached. The rest is dropped.
    return new AsyncConnection(
        options.getTransport(),
        options.getTransportGate(),
        eventCache,
        sessionCache,
        options.getCacheDirSize(),
        options);
  }
}
