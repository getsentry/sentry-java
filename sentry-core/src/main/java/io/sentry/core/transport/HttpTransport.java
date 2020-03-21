package io.sentry.core.transport;

import static io.sentry.core.SentryLevel.DEBUG;
import static io.sentry.core.SentryLevel.ERROR;
import static io.sentry.core.SentryLevel.WARNING;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.ISerializer;
import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.HttpsURLConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An implementation of the {@link ITransport} interface that sends the events to the Sentry server
 * over HTTP(S) in UTF-8 encoding.
 */
@Open
@ApiStatus.NonExtendable // only not final because of testing
@ApiStatus.Internal
public class HttpTransport implements ITransport {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @Nullable Proxy proxy;
  private final @NotNull IConnectionConfigurator connectionConfigurator;
  private final @NotNull ISerializer serializer;
  private final int connectionTimeout;
  private final int readTimeout;
  private final boolean bypassSecurity;
  private final @NotNull URL sentryUrl;
  private final @NotNull SentryOptions options;

  private final @NotNull Map<String, Date> sentryRetryAfterLimit = new ConcurrentHashMap<>();

  private static final int HTTP_RETRY_AFTER_DEFAULT_DELAY_MS = 60000;

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
   * @param sentryUrl sentryUrl which is the parsed DSN
   */
  public HttpTransport(
      final @NotNull SentryOptions options,
      final @NotNull IConnectionConfigurator connectionConfigurator,
      final int connectionTimeoutMills,
      final int readTimeoutMills,
      final boolean bypassSecurity,
      final @NotNull URL sentryUrl) {
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
  protected @NotNull HttpURLConnection open(final @Nullable Proxy proxy) throws IOException {
    // why do we need url here? its not used
    return (HttpURLConnection)
        (proxy == null ? sentryUrl.openConnection() : sentryUrl.openConnection(proxy));
  }

  @Override
  public @NotNull TransportResult send(final @NotNull SentryEvent event) throws IOException {
    final HttpURLConnection connection = createConnection(false);

    int responseCode = -1;

    try (final OutputStream outputStream = connection.getOutputStream();
        final Writer writer = new OutputStreamWriter(outputStream, UTF_8)) {
      serializer.serialize(event, writer);

      // need to also close the input stream of the connection
      connection.getInputStream().close();
      options.getLogger().log(DEBUG, "Event sent %s successfully.", event.getEventId());
      return TransportResult.success();
    } catch (IOException e) {
      try {
        responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
          options
              .getLogger()
              .log(
                  DEBUG,
                  "Event '"
                      + event.getEventId()
                      + "' was rejected by the Sentry server due to a filter.");
        }
        logErrorInPayload(connection);
        return TransportResult.error(responseCode);
      } catch (IOException responseCodeException) {
        // this should not stop us from continuing. We'll just use -1 as response code.
        options
            .getLogger()
            .log(WARNING, "Failed to obtain response code while analyzing event send failure.", e);
      }

      logErrorInPayload(connection);
      return TransportResult.error(responseCode);
    } finally {
      updateRetryAfterLimits(connection, responseCode);

      connection.disconnect();
    }
  }

  @Override
  public boolean isRetryAfter(final @NotNull String type) {
    if (sentryRetryAfterLimit.containsKey(type)) {
      final Date date = sentryRetryAfterLimit.get(type);

      return !new Date().after(date);
    } else if (sentryRetryAfterLimit.containsKey("default")) {
      final Date date = sentryRetryAfterLimit.get("default");

      return !new Date().after(date);
    }
    return false;
  }

  private @NotNull HttpURLConnection createConnection(boolean asEnvelope) throws IOException {
    final HttpURLConnection connection = open(proxy);
    connectionConfigurator.configure(connection);

    connection.setRequestMethod("POST");
    connection.setDoOutput(true);

    connection.setRequestProperty("Content-Encoding", "UTF-8");

    String contentType = "application/json";
    if (asEnvelope) {
      contentType = "application/x-sentry-envelope";
    }
    connection.setRequestProperty("Content-Type", contentType);
    connection.setRequestProperty("Accept", "application/json");

    // https://stackoverflow.com/questions/52726909/java-io-ioexception-unexpected-end-of-stream-on-connection/53089882
    connection.setRequestProperty("Connection", "close");

    connection.setConnectTimeout(connectionTimeout);
    connection.setReadTimeout(readTimeout);

    if (bypassSecurity && connection instanceof HttpsURLConnection) {
      ((HttpsURLConnection) connection).setHostnameVerifier((__, ___) -> true);
    }

    connection.connect();
    return connection;
  }

  @Override
  public @NotNull TransportResult send(final @NotNull SentryEnvelope envelope) throws IOException {
    final HttpURLConnection connection = createConnection(true);

    int responseCode = -1;

    try (final OutputStream outputStream = connection.getOutputStream();
        final Writer writer = new OutputStreamWriter(outputStream, UTF_8)) {
      serializer.serialize(envelope, writer);

      // need to also close the input stream of the connection
      connection.getInputStream().close();
      options
          .getLogger()
          .log(DEBUG, "Envelope sent %s successfully.", envelope.getHeader().getEventId());
      return TransportResult.success();
    } catch (IOException e) {
      try {
        responseCode = connection.getResponseCode();

        // TODO: 403 part of the protocol?
        if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
          options
              .getLogger()
              .log(
                  DEBUG,
                  "Envelope '"
                      + envelope.getHeader().getEventId()
                      + "' was rejected by the Sentry server due to a filter.");
        }
        logErrorInPayload(connection);
        return TransportResult.error(responseCode);
      } catch (IOException responseCodeException) {
        // this should not stop us from continuing. We'll just use -1 as response code.
        options
            .getLogger()
            .log(WARNING, "Failed to obtain response code while analyzing event send failure.", e);
      }

      logErrorInPayload(connection);
      return TransportResult.error(responseCode);
    } catch (Exception e) {
      options
          .getLogger()
          .log(WARNING, "Failed to obtain error message while analyzing event send failure.", e);
      return TransportResult.error(-1);
    } finally {
      updateRetryAfterLimits(connection, responseCode);

      connection.disconnect();
    }
  }

  private void updateRetryAfterLimits(
      final @NotNull HttpURLConnection connection, final int responseCode) {
    final String retryAfterHeader = connection.getHeaderField("Retry-After");
    final String sentryRateLimitHeader = connection.getHeaderField("X-Sentry-Rate-Limit");
    updateRetryAfterLimits(sentryRateLimitHeader, retryAfterHeader, responseCode);
  }

  private void updateRetryAfterLimits(
      final @Nullable String sentryRateLimitHeader,
      final @Nullable String retryAfterHeader,
      final int errorCode) {
    if (sentryRateLimitHeader != null) {
      for (String limit : sentryRateLimitHeader.split(",", -1)) {

        // Java 11 or so has strip() :(
        limit = limit.replace(" ", "");

        final String[] retryAfterAndCategories =
            limit.split(":", -1); // we only need for 1st and 2nd item though.

        if (retryAfterAndCategories.length > 0) {
          final String retryAfter = retryAfterAndCategories[0];
          long retryAfterMs = parseRetryAfterOrDefault(retryAfter);

          if (retryAfterAndCategories.length > 1) {
            final String allCategories = retryAfterAndCategories[1];

            if (allCategories != null) {
              final String[] categories = allCategories.split(";", -1);

              for (final String catItem : categories) {
                // we dont care if Date is UTC as we just add the relative seconds
                sentryRetryAfterLimit.put(
                    catItem, new Date(System.currentTimeMillis() + retryAfterMs));
              }
            }
          }
        }
      }
    } else if (errorCode == 429) {
      final long retryAfterMs = parseRetryAfterOrDefault(retryAfterHeader);
      // we dont care if Date is UTC as we just add the relative seconds
      final Date date = new Date(System.currentTimeMillis() + retryAfterMs);
      sentryRetryAfterLimit.put("default", date);
    }
  }

  private long parseRetryAfterOrDefault(final @Nullable String retryAfterHeader) {
    long retryAfterMs = HTTP_RETRY_AFTER_DEFAULT_DELAY_MS;
    if (retryAfterHeader != null) {
      try {
        retryAfterMs =
            (long) (Double.parseDouble(retryAfterHeader) * 1000L); // seconds -> milliseconds
      } catch (NumberFormatException __) {
        // let's use the default then
      }
    }
    return retryAfterMs;
  }

  private void logErrorInPayload(final @NotNull HttpURLConnection connection) {
    if (options
        .isDebug()) { // just because its expensive, but internally isDebug is already checked when
      // .log() is called
      String errorMessage = null;
      final InputStream errorStream = connection.getErrorStream();
      if (errorStream != null) {
        errorMessage = getErrorMessageFromStream(errorStream);
      }
      if (null == errorMessage || errorMessage.isEmpty()) {
        errorMessage = "An exception occurred while submitting the event to the Sentry server.";
      }

      options.getLogger().log(DEBUG, errorMessage);
    }
  }

  private String getErrorMessageFromStream(final @NotNull InputStream errorStream) {
    final BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, UTF_8));
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
      options
          .getLogger()
          .log(
              ERROR,
              "Exception while reading the error message from the connection: " + e2.getMessage());
    }
    return sb.toString();
  }

  @Override
  public void close() throws IOException {}
}
