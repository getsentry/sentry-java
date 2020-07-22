package io.sentry.core.transport;

import static io.sentry.core.SentryLevel.DEBUG;
import static io.sentry.core.SentryLevel.ERROR;
import static io.sentry.core.SentryLevel.INFO;
import static java.net.HttpURLConnection.HTTP_OK;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.ILogger;
import io.sentry.core.ISerializer;
import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryOptions;
import io.sentry.core.util.Objects;
import io.sentry.core.util.StringUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An implementation of the {@link ITransport} interface that sends the events to the Sentry server
 * over HTTP(S).
 */
@Open
@ApiStatus.NonExtendable // only not final because of testing
@ApiStatus.Internal
public class HttpTransport implements ITransport {

  private enum DataCategory {
    All("__all__"),
    Default("default"), // same as Error
    Error("error"),
    Session("session"),
    Attachment("attachment"),
    Transaction("transaction"),
    Security("security"),
    Unknown("unknown");

    private final String category;

    DataCategory(final @NotNull String category) {
      this.category = category;
    }

    public String getCategory() {
      return category;
    }
  }

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @Nullable Proxy proxy;
  private final @NotNull IConnectionConfigurator connectionConfigurator;
  private final @NotNull ISerializer serializer;
  private final int connectionTimeout;
  private final int readTimeout;
  private final boolean bypassSecurity;
  private final @NotNull URL storeUrl;
  private final @NotNull URL envelopeUrl;
  private final @NotNull SentryOptions options;

  private final @NotNull Map<DataCategory, Date> sentryRetryAfterLimit = new ConcurrentHashMap<>();

  private static final int HTTP_RETRY_AFTER_DEFAULT_DELAY_MILLIS = 60000;

  private final @NotNull ICurrentDateProvider currentDateProvider;

  private final @NotNull ILogger logger;

  /**
   * Constructs a new HTTP transport instance. Notably, the provided {@code requestUpdater} must set
   * the appropriate content encoding header for the {@link io.sentry.core.ISerializer} instance
   * obtained from the options.
   *
   * @param options sentry options to read the config from
   * @param connectionConfigurator this consumer is given a chance to set up the request before it
   *     is sent
   * @param connectionTimeoutMillis connection timeout in milliseconds
   * @param readTimeoutMillis read timeout in milliseconds
   * @param bypassSecurity whether to ignore TLS errors
   * @param sentryUrl sentryUrl which is the parsed DSN
   */
  public HttpTransport(
      final @NotNull SentryOptions options,
      final @NotNull IConnectionConfigurator connectionConfigurator,
      final int connectionTimeoutMillis,
      final int readTimeoutMillis,
      final boolean bypassSecurity,
      final @NotNull URL sentryUrl) {
    this(
        options,
        connectionConfigurator,
        connectionTimeoutMillis,
        readTimeoutMillis,
        bypassSecurity,
        sentryUrl,
        CurrentDateProvider.getInstance());
  }

  HttpTransport(
      final @NotNull SentryOptions options,
      final @NotNull IConnectionConfigurator connectionConfigurator,
      final int connectionTimeoutMillis,
      final int readTimeoutMillis,
      final boolean bypassSecurity,
      final @NotNull URL sentryUrl,
      final @NotNull ICurrentDateProvider currentDateProvider) {
    this.proxy = options.getProxy();
    this.connectionConfigurator = connectionConfigurator;
    this.serializer = options.getSerializer();
    this.connectionTimeout = connectionTimeoutMillis;
    this.readTimeout = readTimeoutMillis;
    this.options = options;
    this.bypassSecurity = bypassSecurity;
    this.currentDateProvider =
        Objects.requireNonNull(currentDateProvider, "CurrentDateProvider is required.");
    this.logger = Objects.requireNonNull(options.getLogger(), "Logger is required.");

    try {
      final URI uri = sentryUrl.toURI();
      storeUrl = uri.resolve(uri.getPath() + "/store/").toURL();
      envelopeUrl = uri.resolve(uri.getPath() + "/envelope/").toURL();
    } catch (URISyntaxException | MalformedURLException e) {
      throw new IllegalArgumentException("Failed to compose the Sentry's server URL.", e);
    }
  }

  // giving up on testing this method is probably the simplest way of having the rest of the class
  // testable...
  protected @NotNull HttpURLConnection open(final @Nullable Proxy proxy) throws IOException {
    return open(storeUrl, proxy);
  }

  protected @NotNull HttpURLConnection open(final @NotNull URL url, final @Nullable Proxy proxy)
      throws IOException {
    return (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
  }

  @Override
  public @NotNull TransportResult send(final @NotNull SentryEvent event) throws IOException {
    final HttpURLConnection connection = createConnection(false);
    TransportResult result;

    try (final OutputStream outputStream = connection.getOutputStream();
        final GZIPOutputStream gzip = new GZIPOutputStream(outputStream);
        final Writer writer = new BufferedWriter(new OutputStreamWriter(gzip, UTF_8))) {

      serializer.serialize(event, writer);
    } catch (IOException e) {
      logger.log(
          ERROR, e, "An exception occurred while submitting the event to the Sentry server.");
    } finally {
      result =
          readAndLog(connection, String.format("Event sent %s successfully.", event.getEventId()));
    }
    return result;
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
    final DataCategory dataCategory = getCategoryFromItemType(itemType);
    final Date currentDate = new Date(currentDateProvider.getCurrentTimeMillis());

    // check all categories
    final Date dateAllCategories = sentryRetryAfterLimit.get(DataCategory.All);
    if (dateAllCategories != null) {
      if (!currentDate.after(dateAllCategories)) {
        return true;
      }
    }

    // Unknown should not be rate limited
    if (DataCategory.Unknown.equals(dataCategory)) {
      return false;
    }

    // check for specific dataCategory
    final Date dateCategory = sentryRetryAfterLimit.get(dataCategory);
    if (dateCategory != null) {
      return !currentDate.after(dateCategory);
    }

    return false;
  }

  /**
   * Returns a rate limiting category from item itemType
   *
   * @param itemType the item itemType (eg event, session, attachment, ...)
   * @return the DataCategory eg (DataCategory.Error, DataCategory.Session, DataCategory.Attachment)
   */
  private @NotNull DataCategory getCategoryFromItemType(final @NotNull String itemType) {
    switch (itemType) {
      case "event":
        return DataCategory.Error;
      case "session":
        return DataCategory.Session;
      case "attachment":
        return DataCategory.Attachment;
      case "transaction":
        return DataCategory.Transaction;
      default:
        return DataCategory.Unknown;
    }
  }

  /**
   * Create a HttpURLConnection connection Sets specific content-type if its an envelope or not
   *
   * @param asEnvelope if its an envelope or not
   * @return the HttpURLConnection
   * @throws IOException if connection has a problem
   */
  private @NotNull HttpURLConnection createConnection(boolean asEnvelope) throws IOException {
    String contentType = "application/json";
    HttpURLConnection connection;
    if (asEnvelope) {
      connection = open(envelopeUrl, proxy);
      contentType = "application/x-sentry-envelope";
    } else {
      connection = open(proxy);
    }
    connectionConfigurator.configure(connection);

    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setChunkedStreamingMode(0);

    connection.setRequestProperty("Content-Encoding", "gzip");
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
    TransportResult result;

    try (final OutputStream outputStream = connection.getOutputStream();
        final GZIPOutputStream gzip = new GZIPOutputStream(outputStream);
        final Writer writer = new BufferedWriter(new OutputStreamWriter(gzip, UTF_8))) {

      serializer.serialize(envelope, writer);
    } catch (Exception e) {
      logger.log(
          ERROR, e, "An exception occurred while submitting the envelope to the Sentry server.");
    } finally {
      result = readAndLog(connection, "Envelope sent successfully.");
    }
    return result;
  }

  /**
   * Read responde code, retry after header and its error stream if there are errors and log it
   *
   * @param connection the HttpURLConnection
   * @param message the message, if custom message if its an event or envelope
   * @return TransportResult.success if responseCode is 200 or TransportResult.error otherwise
   */
  private @NotNull TransportResult readAndLog(
      final @NotNull HttpURLConnection connection, final @NotNull String message) {
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

      logger.log(DEBUG, message);

      return TransportResult.success();
    } catch (IOException e) {
      logger.log(ERROR, e, "Error reading and logging the response stream");
    } finally {
      closeAndDisconnect(connection);
    }
    return TransportResult.error();
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
   * Read retry after headers and update the rate limit Dictionary
   *
   * @param connection the HttpURLConnection
   * @param responseCode the responseCode
   */
  private void updateRetryAfterLimits(
      final @NotNull HttpURLConnection connection, final int responseCode) {
    // seconds
    final String retryAfterHeader = connection.getHeaderField("Retry-After");

    // X-Sentry-Rate-Limits looks like: seconds:categories:scope
    // it could have more than one scope so it looks like:
    // quota_limit, quota_limit, quota_limit

    // a real example: 50:transaction:key, 2700:default;error;security:organization
    // 50::key is also a valid case, it means no categories and it should apply to all of them
    final String sentryRateLimitHeader = connection.getHeaderField("X-Sentry-Rate-Limits");
    updateRetryAfterLimits(sentryRateLimitHeader, retryAfterHeader, responseCode);
  }

  /**
   * Reads and update the rate limit Dictionary
   *
   * @param sentryRateLimitHeader the sentry rate limit header
   * @param retryAfterHeader the retry after header
   * @param errorCode the error code if set
   */
  @SuppressWarnings("JdkObsolete")
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
          long retryAfterMillis = parseRetryAfterOrDefault(retryAfter);

          if (retryAfterAndCategories.length > 1) {
            final String allCategories = retryAfterAndCategories[1];

            // we dont care if Date is UTC as we just add the relative seconds
            final Date date =
                new Date(currentDateProvider.getCurrentTimeMillis() + retryAfterMillis);

            if (allCategories != null && !allCategories.isEmpty()) {
              final String[] categories = allCategories.split(";", -1);

              for (final String catItem : categories) {
                DataCategory dataCategory = DataCategory.Unknown;
                try {
                  dataCategory = DataCategory.valueOf(StringUtils.capitalize(catItem));
                } catch (IllegalArgumentException e) {
                  logger.log(INFO, e, "Unknown category: %s", catItem);
                }
                // we dont apply rate limiting for unknown categories
                if (DataCategory.Unknown.equals(dataCategory)) {
                  continue;
                }
                applyRetryAfterOnlyIfLonger(dataCategory, date);
              }
            } else {
              // if categories are empty, we should apply to "all" categories.
              applyRetryAfterOnlyIfLonger(DataCategory.All, date);
            }
          }
        }
      }
    } else if (errorCode == 429) {
      final long retryAfterMillis = parseRetryAfterOrDefault(retryAfterHeader);
      // we dont care if Date is UTC as we just add the relative seconds
      final Date date = new Date(currentDateProvider.getCurrentTimeMillis() + retryAfterMillis);
      applyRetryAfterOnlyIfLonger(DataCategory.All, date);
    }
  }

  /**
   * apply new timestamp for rate limiting only if its longer than the previous one
   *
   * @param dataCategory the DataCategory
   * @param date the Date to be applied
   */
  @SuppressWarnings("JdkObsolete")
  private void applyRetryAfterOnlyIfLonger(
      final @NotNull DataCategory dataCategory, final @NotNull Date date) {
    final Date oldDate = sentryRetryAfterLimit.get(dataCategory);

    // only overwrite its previous date if the limit is even longer
    if (oldDate == null || date.after(oldDate)) {
      sentryRetryAfterLimit.put(dataCategory, date);
    }
  }

  /**
   * Parses a millis string to a seconds number
   *
   * @param retryAfterHeader the header
   * @return the millis in seconds or the default seconds value
   */
  private long parseRetryAfterOrDefault(final @Nullable String retryAfterHeader) {
    long retryAfterMillis = HTTP_RETRY_AFTER_DEFAULT_DELAY_MILLIS;
    if (retryAfterHeader != null) {
      try {
        retryAfterMillis =
            (long) (Double.parseDouble(retryAfterHeader) * 1000L); // seconds -> milliseconds
      } catch (NumberFormatException ignored) {
        // let's use the default then
      }
    }
    return retryAfterMillis;
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

  @Override
  public void close() throws IOException {
    // a connection is opened and closed for each request, so this method is not used at all.
  }
}
