package io.sentry.android.core;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;

import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryEnvelope;
import io.sentry.SentryOptions;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.zip.GZIPOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpotlightIntegration
    implements Integration, SentryOptions.BeforeEnvelopeCallback, Closeable {

  private @Nullable SentryOptions options;

  // developer machine should be the same across emulators
  // see https://developer.android.com/studio/run/emulator-networking.html
  private final @NotNull String SPOTLIGHT_URL = "http://10.0.2.2:8969/stream";
  private final @NotNull ExecutorService executorService = Executors.newSingleThreadExecutor();

  @Override
  public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
    this.options = options;

    // todo check if we're on the emulator
    if (options.getBeforeEnvelopeCallback() == null) {
      options.setBeforeEnvelopeCallback(this);
    }
  }

  @Override
  public @Nullable SentryEnvelope execute(@NotNull SentryEnvelope envelope, @Nullable Hint hint) {
    if (options == null) {
      return envelope;
    }
    try {
      executorService.execute(() -> sendEnvelope(envelope));
    } catch (RejectedExecutionException ex) {
      // ignored
    }
    return envelope;
  }

  private void sendEnvelope(SentryEnvelope envelope) {
    try {
      final HttpURLConnection connection = createConnection();
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
    } catch (IOException e) {
      options
          .getLogger()
          .log(ERROR, e, "An exception occurred while creating the connection to spotlight.");
    }
  }

  private @NotNull HttpURLConnection createConnection() throws IOException {

    final @NotNull HttpURLConnection connection =
        (HttpURLConnection) URI.create(SPOTLIGHT_URL).toURL().openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);

    connection.setRequestProperty("Content-Encoding", "gzip");
    connection.setRequestProperty("Content-Type", "application/x-sentry-envelope");
    connection.setRequestProperty("Accept", "application/json");

    // https://stackoverflow.com/questions/52726909/java-io-ioexception-unexpected-end-of-stream-on-connection/53089882
    connection.setRequestProperty("Connection", "close");

    //    connection.setConnectTimeout(options.getConnectionTimeoutMillis());
    //    connection.setReadTimeout(options.getReadTimeoutMillis());

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
