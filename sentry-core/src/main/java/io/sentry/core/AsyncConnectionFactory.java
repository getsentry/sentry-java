package io.sentry.core;

import io.sentry.core.cache.IEventCache;
import io.sentry.core.transport.*;
import java.net.MalformedURLException;
import java.net.URL;

final class AsyncConnectionFactory {
  public static AsyncConnection create(SentryOptions options, IEventCache eventCache) {
    try {
      Dsn parsedDsn = new Dsn(options.getDsn());
      IConnectionConfigurator setCredentials =
          new CredentialsSettingConfigurator(parsedDsn, options.getSentryClientName());
      URL sentryUrl = parsedDsn.getSentryUri().toURL();

      // TODO: Take configuration values from SentryOptions
      HttpTransport transport =
          new HttpTransport(options, setCredentials, 5000, 5000, false, sentryUrl);

      // TODO this should be made configurable at least for the Android case where we can
      // just not attempt to send if the device is offline.
      ITransportGate alwaysOn = () -> true;

      IBackOffIntervalStrategy linearBackoff = attempt -> attempt * 500;

      // the connection doesn't do any retries of failed sends and can hold at most the same number
      // of
      // pending events as there are being cached. The rest is dropped.
      return new AsyncConnection(
          transport,
          alwaysOn,
          linearBackoff,
          eventCache,
          0,
          options.getCacheDirSize(),
          true,
          options);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(
          "Failed to compose the connection to the Sentry server.", e);
    }
  }
}
