package io.sentry.transport;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;
import static java.net.HttpURLConnection.HTTP_OK;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.ILogger;
import io.sentry.ISerializer;
import io.sentry.SentryEnvelope;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * An implementation of the {@link ITransport} interface that sends the events to the Sentry server
 * over HTTP(S).
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
  private final @NotNull URL envelopeUrl;
  private final @Nullable SSLSocketFactory sslSocketFactory;
  private final @Nullable HostnameVerifier hostnameVerifier;

  private final @NotNull SentryOptions options;

  private final @NotNull ILogger logger;

  private final @NotNull RateLimiter rateLimiter;

  /**
   * Constructs a new HTTP transport instance. Notably, the provided {@code requestUpdater} must set
   * the appropriate content encoding header for the {@link io.sentry.ISerializer} instance obtained
   * from the options.
   *
   * @param options sentry options to read the config from
   * @param connectionConfigurator this consumer is given a chance to set up the request before it
   *     is sent
   * @param connectionTimeoutMillis connection timeout in milliseconds
   * @param readTimeoutMillis read timeout in milliseconds
   * @param sslSocketFactory custom sslSocketFactory for self-signed certificate trust
   * @param hostnameVerifier custom hostnameVerifier for self-signed certificate trust
   * @param sentryUrl sentryUrl which is the parsed DSN
   */
  public HttpTransport(
      final @NotNull SentryOptions options,
      final @NotNull IConnectionConfigurator connectionConfigurator,
      final int connectionTimeoutMillis,
      final int readTimeoutMillis,
      final @Nullable SSLSocketFactory sslSocketFactory,
      final @Nullable HostnameVerifier hostnameVerifier,
      final @NotNull URL sentryUrl) {
    this(
        options,
        connectionConfigurator,
        connectionTimeoutMillis,
        readTimeoutMillis,
        sslSocketFactory,
        hostnameVerifier,
        sentryUrl,
        CurrentDateProvider.getInstance(),
        AuthenticatorWrapper.getInstance());
  }

  HttpTransport(
      final @NotNull SentryOptions options,
      final @NotNull IConnectionConfigurator connectionConfigurator,
      final int connectionTimeoutMillis,
      final int readTimeoutMillis,
      final @Nullable SSLSocketFactory sslSocketFactory,
      final @Nullable HostnameVerifier hostnameVerifier,
      final @NotNull URL sentryUrl,
      final @NotNull ICurrentDateProvider currentDateProvider,
      final @NotNull AuthenticatorWrapper authenticatorWrapper) {
    this.connectionConfigurator = connectionConfigurator;
    this.serializer = options.getSerializer();
    this.connectionTimeout = connectionTimeoutMillis;
    this.readTimeout = readTimeoutMillis;
    this.options = options;
    this.sslSocketFactory = sslSocketFactory;
    this.hostnameVerifier = hostnameVerifier;
    this.logger = Objects.requireNonNull(options.getLogger(), "Logger is required.");

    try {
      final URI uri = sentryUrl.toURI();
      envelopeUrl = uri.resolve(uri.getPath() + "/envelope/").toURL();
    } catch (URISyntaxException | MalformedURLException e) {
      throw new IllegalArgumentException("Failed to compose the Sentry's server URL.", e);
    }

    this.proxy = resolveProxy(options.getProxy());

    if (proxy != null && options.getProxy() != null) {
      final String proxyUser = options.getProxy().getUser();
      final String proxyPassword = options.getProxy().getPass();

      if (proxyUser != null && proxyPassword != null) {
        authenticatorWrapper.setDefault(new ProxyAuthenticator(proxyUser, proxyPassword));
      }
    }
    this.rateLimiter = new RateLimiter(currentDateProvider, options.getLogger());
  }

  private @Nullable Proxy resolveProxy(final @Nullable SentryOptions.Proxy optionsProxy) {
    Proxy proxy = null;
    if (optionsProxy != null) {
      final String port = optionsProxy.getPort();
      final String host = optionsProxy.getHost();
      if (port != null && host != null) {
        try {
          InetSocketAddress proxyAddr = new InetSocketAddress(host, Integer.parseInt(port));
          proxy = new Proxy(Proxy.Type.HTTP, proxyAddr);
        } catch (NumberFormatException e) {
          logger.log(
              ERROR,
              e,
              "Failed to parse Sentry Proxy port: "
                  + optionsProxy.getPort()
                  + ". Proxy is ignored");
        }
      }
    }
    return proxy;
  }

  protected @NotNull HttpURLConnection open() throws IOException {
    return (HttpURLConnection)
        (proxy == null ? envelopeUrl.openConnection() : envelopeUrl.openConnection(proxy));
  }

  /**
   * Check if an itemType is retry after or not
   *
   * @param itemType the itemType (eg event, session, etc...)
   * @return true if retry after or false otherwise
   */
  @SuppressWarnings("JdkObsolete")
  @Override
  public boolean isRetryAfter(final @NotNull String itemType) {
    return rateLimiter.isRetryAfter(itemType);
  }

  /**
   * Create a HttpURLConnection connection Sets specific content-type if its an envelope or not
   *
   * @return the HttpURLConnection
   * @throws IOException if connection has a problem
   */
  private @NotNull HttpURLConnection createConnection() throws IOException {
    HttpURLConnection connection = open();
    connectionConfigurator.configure(connection);

    connection.setRequestMethod("POST");
    connection.setDoOutput(true);

    connection.setRequestProperty("Content-Encoding", "gzip");
    connection.setRequestProperty("Content-Type", "application/x-sentry-envelope");
    connection.setRequestProperty("Accept", "application/json");

    // https://stackoverflow.com/questions/52726909/java-io-ioexception-unexpected-end-of-stream-on-connection/53089882
    connection.setRequestProperty("Connection", "close");

    connection.setConnectTimeout(connectionTimeout);
    connection.setReadTimeout(readTimeout);

    if (connection instanceof HttpsURLConnection && hostnameVerifier != null) {
      ((HttpsURLConnection) connection).setHostnameVerifier(hostnameVerifier);
    }
    if (connection instanceof HttpsURLConnection && sslSocketFactory != null) {
      ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
    }

    connection.connect();
    return connection;
  }

  @Override
  public @NotNull TransportResult send(final @NotNull SentryEnvelope envelope) throws IOException {
    final HttpURLConnection connection = createConnection();
    TransportResult result;

    try (final OutputStream outputStream = connection.getOutputStream();
        final GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
      serializer.serialize(envelope, gzip);
    } catch (Exception e) {
      logger.log(
          ERROR, e, "An exception occurred while submitting the envelope to the Sentry server.");
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
        logger.log(ERROR, "Request failed, API returned %s", responseCode);
        // double check because call is expensive
        if (options.isDebug()) {
          String errorMessage = getErrorMessageFromStream(connection);
          logger.log(ERROR, errorMessage);
        }

        return TransportResult.error(responseCode);
      }

      logger.log(DEBUG, "Envelope sent successfully.");

      return TransportResult.success();
    } catch (IOException e) {
      logger.log(ERROR, e, "Error reading and logging the response stream");
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
  Proxy getProxy() {
    return proxy;
  }

  @Override
  public void close() throws IOException {
    // a connection is opened and closed for each request, so this method is not used at all.
  }
}
