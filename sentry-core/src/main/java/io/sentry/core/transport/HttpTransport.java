package io.sentry.core.transport;

import static io.sentry.core.ILogger.logIfNotNull;
import static io.sentry.core.SentryLevel.*;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.ISerializer;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryOptions;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import javax.net.ssl.HttpsURLConnection;
import org.jetbrains.annotations.Nullable;

/**
 * An implementation of the {@link ITransport} interface that sends the events to the Sentry server
 * over HTTP(S) in UTF-8 encoding.
 */
@Open // TODO: make it final and disable nopen check for testing
public class HttpTransport implements ITransport {
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  @Nullable private final Proxy proxy;
  private final IConnectionConfigurator connectionConfigurator;
  private final ISerializer serializer;
  private final int connectionTimeout;
  private final int readTimeout;
  private final boolean bypassSecurity;
  private final URL sentryUrl;
  private final SentryOptions options;

  /**
   * Constructs a new HTTP transport instance. Notably, the provided {@code requestUpdater} must set
   * the appropriate content encoding header for the {@link io.sentry.core.ISerializer} instance
   * obtained from the options.
   *
   * @param options sentry options to read the config from
   * @param connectionConfigurator this consumer is given a chance to set up the request before it
   *     is sent
   * @param connectionTimeoutMills connection timeout in milliseconds
   * @param readTimeoutMills read timeout in milliseconds
   * @param bypassSecurity whether to ignore TLS errors
   */
  public HttpTransport(
      SentryOptions options,
      IConnectionConfigurator connectionConfigurator,
      int connectionTimeoutMills,
      int readTimeoutMills,
      boolean bypassSecurity,
      URL sentryUrl) {
    this.proxy = options.getProxy();
    this.connectionConfigurator = connectionConfigurator;
    this.serializer = options.getSerializer();
    this.connectionTimeout = connectionTimeoutMills;
    this.readTimeout = readTimeoutMills;
    this.options = options;
    this.bypassSecurity = bypassSecurity;
    this.sentryUrl = sentryUrl;
  }

  // giving up on testing this method is probably the simplest way of having the rest of the class
  // testable...
  protected HttpURLConnection open(URL url, Proxy proxy) throws IOException {
    // why do we need url here? its not used
    return (HttpURLConnection)
        (proxy == null ? sentryUrl.openConnection() : sentryUrl.openConnection(proxy));
  }

  @Override
  public TransportResult send(SentryEvent event) throws IOException {
    HttpURLConnection connection = open(sentryUrl, proxy);
    connectionConfigurator.configure(connection);

    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Encoding", "UTF-8");
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Accept", "application/json");

    // https://stackoverflow.com/questions/52726909/java-io-ioexception-unexpected-end-of-stream-on-connection/53089882
    connection.setRequestProperty("Connection", "close");

    connection.setConnectTimeout(connectionTimeout);
    connection.setReadTimeout(readTimeout);

    if (bypassSecurity && connection instanceof HttpsURLConnection) {
      ((HttpsURLConnection) connection).setHostnameVerifier((__, ___) -> true);
    }

    connection.connect();

    try (OutputStream outputStream = connection.getOutputStream()) {
      serializer.serialize(event, new OutputStreamWriter(outputStream, UTF_8));

      // need to also close the input stream of the connection
      connection.getInputStream().close();
      return TransportResult.success();
    } catch (IOException e) {
      long retryAfterMs = 1000; // the default is 1s
      String retryAfterHeader = connection.getHeaderField("Retry-After");
      if (retryAfterHeader != null) {
        try {
          retryAfterMs =
              (long) (Double.parseDouble(retryAfterHeader) * 1000L); // seconds -> milliseconds
        } catch (NumberFormatException __) {
          // let's use the default then
        }
      }

      int responseCode = -1;
      try {
        responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
          if (options.isDebug()) {
            logIfNotNull(
                options.getLogger(),
                DEBUG,
                "Event '"
                    + event.getEventId()
                    + "' was rejected by the Sentry server due to a filter.");
          }
        }
        logErrorInPayload(connection);
        return TransportResult.error(retryAfterMs, responseCode);
      } catch (IOException responseCodeException) {
        // this should not stop us from continuing. We'll just use -1 as response code.
        logIfNotNull(
            options.getLogger(),
            WARNING,
            "Failed to obtain response code while analyzing event send failure.",
            e);
      }

      logErrorInPayload(connection);
      return TransportResult.error(retryAfterMs, responseCode);
    } finally {
      connection.disconnect();
    }
  }

  private void logErrorInPayload(HttpURLConnection connection) {
    if (options.isDebug()) {
      String errorMessage = null;
      final InputStream errorStream = connection.getErrorStream();
      if (errorStream != null) {
        errorMessage = getErrorMessageFromStream(errorStream);
      }
      if (null == errorMessage || errorMessage.isEmpty()) {
        errorMessage = "An exception occurred while submitting the event to the Sentry server.";
      }

      logIfNotNull(options.getLogger(), DEBUG, errorMessage);
    }
  }

  private String getErrorMessageFromStream(InputStream errorStream) {
    BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, UTF_8));
    StringBuilder sb = new StringBuilder();
    try {
      String line;
      // ensure we do not add "\n" to the last line
      boolean first = true;
      while ((line = reader.readLine()) != null) {
        if (!first) {
          sb.append("\n");
        }
        sb.append(line);
        first = false;
      }
    } catch (Exception e2) {
      logIfNotNull(
          options.getLogger(),
          ERROR,
          "Exception while reading the error message from the connection: " + e2.getMessage());
    }
    return sb.toString();
  }

  @Override
  public void close() throws IOException {}
}
