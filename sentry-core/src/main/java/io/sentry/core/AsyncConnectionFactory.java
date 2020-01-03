package io.sentry.core;

import io.sentry.core.cache.IEventCache;
import io.sentry.core.transport.AsyncConnection;
import io.sentry.core.transport.IBackOffIntervalStrategy;
import io.sentry.core.transport.ITransportGate;

final class AsyncConnectionFactory {
  private AsyncConnectionFactory() {}

  public static AsyncConnection create(SentryOptions options, IEventCache eventCache) {
    // TODO this should be made configurable at least for the Android case where we can
    // just not attempt to send if the device is offline.
    ITransportGate alwaysOn = () -> true;

    IBackOffIntervalStrategy linearBackoff = attempt -> attempt * 500;

    // the connection doesn't do any retries of failed sends and can hold at most the same number
    // of pending events as there are being cached. The rest is dropped.
    return new AsyncConnection(
        options.getTransport(),
        alwaysOn,
        linearBackoff,
        eventCache,
        0,
        options.getCacheDirSize(),
        options);
  }
}
