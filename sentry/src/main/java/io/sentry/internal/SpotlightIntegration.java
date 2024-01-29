package io.sentry.internal;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.zip.GZIPOutputStream;

import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryEnvelope;
import io.sentry.SentryOptions;
import io.sentry.util.Objects;

@ApiStatus.Internal
public final class SpotlightIntegration
  implements Integration, SentryOptions.BeforeEnvelopeCallback, Closeable {

  private @NotNull SentryOptions options = SentryOptions.empty();

  private final @NotNull ExecutorService executorService = Executors.newSingleThreadExecutor();

  @Override
  public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
    this.options = options;

    if (options.getBeforeEnvelopeCallback() == null && options.getSpotlightConnectionUrl() != null) {
      options.setBeforeEnvelopeCallback(this);
    }
  }

  @Override
  public @NotNull SentryEnvelope execute(@NotNull SentryEnvelope envelope, @Nullable Hint hint) {
    try {
      executorService.execute(() -> sendEnvelope(envelope));
    } catch (RejectedExecutionException ex) {
      // ignored
    }
    return envelope;
  }

  private void sendEnvelope(final @NotNull SentryEnvelope envelope) {
    try {
      final @NotNull String spotlightConnectionUrl = Objects.requireNonNull(options.getSpotlightConnectionUrl(), "Spotlight URL can't be null");

      final HttpURLConnection connection = createConnection(spotlightConnectionUrl);
      try (final OutputStream outputStream = connection.getOutputStream();
           final GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
        options.getSerializer().serialize(envelope, gzip);
      } catch (Throwable e) {
        options.getLogger()
          .log(
            ERROR,
            e,
            "An exception occurred while submitting the envelope to the Sentry server.");
      } finally {
        final int responseCode = connection.getResponseCode();
        options.getLogger().log(DEBUG, "Envelope sent to spotlight: %d", responseCode);
        closeAndDisconnect(connection);
      }
    } catch (final Exception e) {
      options.getLogger()
        .log(ERROR, e, "An exception occurred while creating the connection to spotlight.");
    }
  }

  private @NotNull HttpURLConnection createConnection(final @NotNull String url) throws Exception {

    final @NotNull HttpURLConnection connection =
      (HttpURLConnection) URI.create(url).toURL().openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);

    connection.setRequestProperty("Content-Encoding", "gzip");
    connection.setRequestProperty("Content-Type", "application/x-sentry-envelope");
    connection.setRequestProperty("Accept", "application/json");

    // https://stackoverflow.com/questions/52726909/java-io-ioexception-unexpected-end-of-stream-on-connection/53089882
    connection.setRequestProperty("Connection", "close");

    connection.connect();
    return connection;
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

  @Override
  public void close() throws IOException {
    executorService.shutdown();
  }
}
