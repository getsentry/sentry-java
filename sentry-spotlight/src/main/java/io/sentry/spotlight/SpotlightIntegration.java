package io.sentry.spotlight;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.WARNING;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.ISentryExecutorService;
import io.sentry.Integration;
import io.sentry.NoOpLogger;
import io.sentry.NoOpSentryExecutorService;
import io.sentry.SentryEnvelope;
import io.sentry.SentryExecutorService;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryOptions;
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

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-spotlight", BuildConfig.VERSION_NAME);
  }

  private @Nullable SentryOptions options;
  private @NotNull ILogger logger = NoOpLogger.getInstance();
  private @NotNull ISentryExecutorService executorService = NoOpSentryExecutorService.getInstance();

  @Override
  public void register(@NotNull IScopes scopes, @NotNull SentryOptions options) {
    this.options = options;
    this.logger = options.getLogger();

    if (options.getBeforeEnvelopeCallback() == null && options.isEnableSpotlight()) {
      executorService = new SentryExecutorService(options);
      options.setBeforeEnvelopeCallback(this);
      logger.log(DEBUG, "SpotlightIntegration enabled.");
      addIntegrationToSdkVersion("Spotlight");
    } else {
      logger.log(
          DEBUG,
          "SpotlightIntegration is not enabled. "
              + "BeforeEnvelopeCallback is already set or spotlight is not enabled.");
    }
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void execute(final @NotNull SentryEnvelope envelope, final @Nullable Hint hint) {
    try {
      executorService.submit(() -> sendEnvelope(envelope));
    } catch (RejectedExecutionException e) {
      logger.log(WARNING, "Spotlight envelope submission rejected.", e);
    }
  }

  private void sendEnvelope(final @NotNull SentryEnvelope envelope) {
    try {
      if (options == null) {
        throw new IllegalArgumentException("SentryOptions are required to send envelopes.");
      }
      final String spotlightConnectionUrl = getSpotlightConnectionUrl();

      final HttpURLConnection connection = createConnection(spotlightConnectionUrl);
      try (final OutputStream outputStream = connection.getOutputStream();
          final GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
        options.getSerializer().serialize(envelope, gzip);
      } catch (Throwable e) {
        logger.log(
            ERROR, "An exception occurred while submitting the envelope to the Sentry server.", e);
      } finally {
        final int responseCode = connection.getResponseCode();
        logger.log(DEBUG, "Envelope sent to spotlight: %d", responseCode);
        closeAndDisconnect(connection);
      }
    } catch (final Exception e) {
      logger.log(ERROR, "An exception occurred while creating the connection to spotlight.", e);
    }
  }

  @TestOnly
  public String getSpotlightConnectionUrl() {
    if (options != null && options.getSpotlightConnectionUrl() != null) {
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
    if (options != null && options.getBeforeEnvelopeCallback() == this) {
      options.setBeforeEnvelopeCallback(null);
    }
  }
}
