package io.sentry;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;

import io.sentry.util.Platform;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.RejectedExecutionException;
import java.util.zip.GZIPOutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class SpotlightIntegration
    implements Integration, SentryOptions.BeforeEnvelopeCallback, Closeable {

  private @NotNull SentryOptions options = SentryOptions.empty();
  private @NotNull ISentryExecutorService executorService = NoOpSentryExecutorService.getInstance();

  @Override
  public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
    this.options = options;

    if (options.getBeforeEnvelopeCallback() == null && options.isEnableSpotlight()) {
      executorService = new SentryExecutorService();
      options.setBeforeEnvelopeCallback(this);
      options.getLogger().log(DEBUG, "SpotlightIntegration enabled.");
    } else {
      options
          .getLogger()
          .log(
              DEBUG,
              "SpotlightIntegration is not enabled. "
                  + "BeforeEnvelopeCallback is already set or spotlight is not enabled.");
    }
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public @NotNull SentryEnvelope execute(
      final @NotNull SentryEnvelope envelope, final @Nullable Hint hint) {
    try {
      executorService.submit(() -> sendEnvelope(envelope));
    } catch (RejectedExecutionException ex) {
      // ignored
    }
    return envelope;
  }

  private void sendEnvelope(final @NotNull SentryEnvelope envelope) {
    try {
      final String spotlightConnectionUrl = getSpotlightConnectionUrl();

      final HttpURLConnection connection = createConnection(spotlightConnectionUrl);
      try (final OutputStream outputStream = connection.getOutputStream();
          final GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
        options.getSerializer().serialize(envelope, gzip);
      } catch (Throwable e) {
        options
            .getLogger()
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
      options
          .getLogger()
          .log(ERROR, e, "An exception occurred while creating the connection to spotlight.");
    }
  }

  @TestOnly
  public String getSpotlightConnectionUrl() {
    if (options.getSpotlightConnectionUrl() != null) {
      return options.getSpotlightConnectionUrl();
    }
    if (Platform.isAndroid()) {
      // developer machine should be the same across emulators
      // see https://developer.android.com/studio/run/emulator-networking.html
      return "http://10.0.2.2:8969/stream";
    } else {
      return "http://localhost:8969/stream";
    }
  }

  private @NotNull HttpURLConnection createConnection(final @NotNull String url) throws Exception {

    final @NotNull HttpURLConnection connection =
        (HttpURLConnection) URI.create(url).toURL().openConnection();

    connection.setReadTimeout(1000);
    connection.setConnectTimeout(1000);
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
    executorService.close(0);
    if (options.getBeforeEnvelopeCallback() == this) {
      options.setBeforeEnvelopeCallback(null);
    }
  }
}
