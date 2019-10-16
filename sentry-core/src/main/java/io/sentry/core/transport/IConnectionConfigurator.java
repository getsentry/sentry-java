package io.sentry.core.transport;

import java.net.HttpURLConnection;

/**
 * A companion interface to {@link HttpTransport} implementations of which prepare the connection
 * with necessary headers and other settings before an event is sent.
 */
public interface IConnectionConfigurator {
  void configure(HttpURLConnection connection);
}
