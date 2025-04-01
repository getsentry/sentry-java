package io.sentry.transport;

import static io.sentry.SentryLevel.ERROR;
import static java.net.HttpURLConnection.HTTP_OK;

import io.sentry.RequestDetails;
import io.sentry.SentryEnvelope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class HttpConnection {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @Nullable Proxy proxy;
  private final @NotNull RequestDetails requestDetails;
  private final @NotNull SentryOptions options;
  private final @NotNull RateLimiter rateLimiter;

  /**
   * Constructs a new HTTP transport instance. Notably, the provided {@code requestUpdater} must set
   * the appropriate content encoding header for the {@link io.sentry.ISerializer} instance obtained
   * from the options.
   *
   * @param options sentry options to read the config from
   * @param requestDetails request details
   * @param rateLimiter rate limiter
   */
  public HttpConnection(
      final @NotNull SentryOptions options,
      final @NotNull RequestDetails requestDetails,
      final @NotNull RateLimiter rateLimiter) {
    this(options, requestDetails, AuthenticatorWrapper.getInstance(), rateLimiter);
  }

  HttpConnection(
      final @NotNull SentryOptions options,
      final @NotNull RequestDetails requestDetails,
      final @NotNull AuthenticatorWrapper authenticatorWrapper,
      final @NotNull RateLimiter rateLimiter) {
    this.requestDetails = requestDetails;
    this.options = options;
    this.rateLimiter = rateLimiter;

    this.proxy = resolveProxy(options.getProxy());

    if (proxy != null && options.getProxy() != null) {
      final String proxyUser = options.getProxy().getUser();
      final String proxyPassword = options.getProxy().getPass();

      if (proxyUser != null && proxyPassword != null) {
        authenticatorWrapper.setDefault(new ProxyAuthenticator(proxyUser, proxyPassword));
      }
    }
  }

  private @Nullable Proxy resolveProxy(final @Nullable SentryOptions.Proxy optionsProxy) {
    Proxy proxy = null;
    if (optionsProxy != null) {
      final String port = optionsProxy.getPort();
      final String host = optionsProxy.getHost();
      if (port != null && host != null) {
        try {
          final @NotNull Proxy.Type type;
          if (optionsProxy.getType() != null) {
            type = optionsProxy.getType();
          } else {
            type = Proxy.Type.HTTP;
          }
          InetSocketAddress proxyAddr = new InetSocketAddress(host, Integer.parseInt(port));
          proxy = new Proxy(type, proxyAddr);
        } catch (NumberFormatException e) {
          if (options.getLogger().isEnabled(ERROR)) {
            options
                .getLogger()
                .log(
                    ERROR,
                    e,
                    "Failed to parse Sentry Proxy port: "
                        + optionsProxy.getPort()
                        + ". Proxy is ignored");
          }
        }
      }
    }
    return proxy;
  }

  @NotNull
  HttpURLConnection open() throws IOException {
    return (HttpURLConnection)
        (proxy == null
            ? requestDetails.getUrl().openConnection()
            : requestDetails.getUrl().openConnection(proxy));
  }

  /**
   * Create a HttpURLConnection connection Sets specific content-type if its an envelope or not
   *
   * @return the HttpURLConnection
   * @throws IOException if connection has a problem
   */
  private @NotNull HttpURLConnection createConnection() throws IOException {
    HttpURLConnection connection = open();

    for (Map.Entry<String, String> header : requestDetails.getHeaders().entrySet()) {
      connection.setRequestProperty(header.getKey(), header.getValue());
    }

    connection.setRequestMethod("POST");
    connection.setDoOutput(true);

    connection.setRequestProperty("Content-Encoding", "gzip");
    connection.setRequestProperty("Content-Type", "application/x-sentry-envelope");
    connection.setRequestProperty("Accept", "application/json");

    // https://stackoverflow.com/questions/52726909/java-io-ioexception-unexpected-end-of-stream-on-connection/53089882
    connection.setRequestProperty("Connection", "close");

    connection.setConnectTimeout(options.getConnectionTimeoutMillis());
    connection.setReadTimeout(options.getReadTimeoutMillis());

    final SSLSocketFactory sslSocketFactory = options.getSslSocketFactory();

    if (connection instanceof HttpsURLConnection && sslSocketFactory != null) {
      ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
    }

    connection.connect();
    return connection;
  }

  public @NotNull TransportResult send(final @NotNull SentryEnvelope envelope) throws IOException {
    final HttpURLConnection connection = createConnection();
    TransportResult result;

    try (final OutputStream outputStream = connection.getOutputStream();
        final GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
      options.getSerializer().serialize(envelope, gzip);
    } catch (Throwable e) {
      if (options.getLogger().isEnabled(ERROR)) {
        options
            .getLogger()
            .log(
                ERROR,
                e,
                "An exception occurred while submitting the envelope to the Sentry server.");
      }
    } finally {
      result = readAndLog(connection);
    }
    return result;
  }

  /**
   * Read responde code, retry after header and its error stream if there are errors and log it
   *
   * @param connection the HttpURLConnection
   * @return TransportResult.success if responseCode is 200 or TransportResult.error otherwise
   */
  private @NotNull TransportResult readAndLog(final @NotNull HttpURLConnection connection) {
    try {
      final int responseCode = connection.getResponseCode();

      updateRetryAfterLimits(connection, responseCode);

      if (!isSuccessfulResponseCode(responseCode)) {
        if (options.getLogger().isEnabled(ERROR)) {
          options.getLogger().log(ERROR, "Request failed, API returned %s", responseCode);
        }
        // double check because call is expensive
        if (options.isDebug()) {
          final @NotNull String errorMessage = getErrorMessageFromStream(connection);
          // the error message may contain anything (including formatting symbols), so provide it as
          // an argument itself
          if (options.getLogger().isEnabled(ERROR)) {
            options.getLogger().log(ERROR, "%s", errorMessage);
          }
        }

        return TransportResult.error(responseCode);
      }

      if (options.getLogger().isEnabled(SentryLevel.DEBUG)) {
        options.getLogger().log(SentryLevel.DEBUG, "Envelope sent successfully.");
      }

      return TransportResult.success();
    } catch (IOException e) {
      if (options.getLogger().isEnabled(ERROR)) {
        options.getLogger().log(ERROR, e, "Error reading and logging the response stream");
      }
    } finally {
      closeAndDisconnect(connection);
    }
    return TransportResult.error();
  }

  /**
   * Read retry after headers and update the rate limit Dictionary
   *
   * @param connection the HttpURLConnection
   * @param responseCode the responseCode
   */
  public void updateRetryAfterLimits(
      final @NotNull HttpURLConnection connection, final int responseCode) {
    // seconds
    final String retryAfterHeader = connection.getHeaderField("Retry-After");

    // X-Sentry-Rate-Limits looks like: seconds:categories:scope
    // it could have more than one scope so it looks like:
    // quota_limit, quota_limit, quota_limit

    // a real example: 50:transaction:key, 2700:default;error;security:organization
    // 50::key is also a valid case, it means no categories and it should apply to all of them
    final String sentryRateLimitHeader = connection.getHeaderField("X-Sentry-Rate-Limits");
    rateLimiter.updateRetryAfterLimits(sentryRateLimitHeader, retryAfterHeader, responseCode);
  }

  /**
   * Closes the Response stream and disconnect the connection
   *
   * @param connection the HttpURLConnection
   */
  private void closeAndDisconnect(final @NotNull HttpURLConnection connection) {
    try {
      connection.getInputStream().close();
    } catch (IOException ignored) {
      // connection is already closed
    } finally {
      connection.disconnect();
    }
  }

  /**
   * Reads the error message from the error stream
   *
   * @param connection the HttpURLConnection
   * @return the error message or null if none
   */
  private @NotNull String getErrorMessageFromStream(final @NotNull HttpURLConnection connection) {
    try (final InputStream errorStream = connection.getErrorStream();
        final BufferedReader reader =
            new BufferedReader(new InputStreamReader(errorStream, UTF_8))) {
      final StringBuilder sb = new StringBuilder();
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
      return sb.toString();
    } catch (IOException e) {
      return "Failed to obtain error message while analyzing send failure.";
    }
  }

  /**
   * Returns if response code is OK=200
   *
   * @param responseCode the response code
   * @return true if it is OK=200 or false otherwise
   */
  private boolean isSuccessfulResponseCode(final int responseCode) {
    return responseCode == HTTP_OK;
  }

  @TestOnly
  @Nullable
  Proxy getProxy() {
    return proxy;
  }
}
