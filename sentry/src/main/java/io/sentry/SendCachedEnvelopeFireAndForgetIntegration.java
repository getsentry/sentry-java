package io.sentry;

import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sends cached events over when your App. is starting. */
public final class SendCachedEnvelopeFireAndForgetIntegration
    implements Integration, IConnectionStatusProvider.IConnectionStatusObserver, Closeable {

  private final @NotNull SendFireAndForgetFactory factory;
  private @Nullable IConnectionStatusProvider connectionStatusProvider;
  private @Nullable IHub hub;
  private @Nullable SentryOptions options;

  public interface SendFireAndForget {
    void send();
  }

  public interface SendFireAndForgetDirPath {
    @Nullable
    String getDirPath();
  }

  public interface SendFireAndForgetFactory {
    @Nullable
    SendFireAndForget create(@NotNull IHub hub, @NotNull SentryOptions options);

    default boolean hasValidPath(final @Nullable String dirPath, final @NotNull ILogger logger) {
      if (dirPath == null || dirPath.isEmpty()) {
        logger.log(SentryLevel.INFO, "No cached dir path is defined in options.");
        return false;
      }
      return true;
    }

    default @NotNull SendFireAndForget processDir(
        final @NotNull DirectoryProcessor directoryProcessor,
        final @NotNull String dirPath,
        final @NotNull ILogger logger) {
      final File dirFile = new File(dirPath);
      return () -> {
        logger.log(SentryLevel.DEBUG, "Started processing cached files from %s", dirPath);

        directoryProcessor.processDirectory(dirFile);

        logger.log(SentryLevel.DEBUG, "Finished processing cached files from %s", dirPath);
      };
    }
  }

  public SendCachedEnvelopeFireAndForgetIntegration(
      final @NotNull SendFireAndForgetFactory factory) {
    this.factory = Objects.requireNonNull(factory, "SendFireAndForgetFactory is required");
  }

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    Objects.requireNonNull(options, "SentryOptions is required");

    this.hub = hub;
    this.options = options;

    final String cachedDir = options.getCacheDirPath();
    if (!factory.hasValidPath(cachedDir, options.getLogger())) {
      options.getLogger().log(SentryLevel.ERROR, "No cache dir path is defined in options.");
      return;
    }

    options
        .getLogger()
        .log(SentryLevel.DEBUG, "SendCachedEventFireAndForgetIntegration installed.");
    addIntegrationToSdkVersion();

    connectionStatusProvider = options.getConnectionStatusProvider();
    connectionStatusProvider.addConnectionStatusObserver(this);

    sendCachedEnvelopes(hub, options);
  }

  @Override
  public void close() throws IOException {
    if (connectionStatusProvider != null) {
      connectionStatusProvider.removeConnectionStatusObserver(this);
    }
  }

  @Override
  public void onConnectionStatusChanged(IConnectionStatusProvider.ConnectionStatus status) {
    if (hub != null && options != null) {
      sendCachedEnvelopes(hub, options);
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private synchronized void sendCachedEnvelopes(@NotNull IHub hub, @NotNull SentryOptions options) {

    // assume we're connected unless overruled by the provider
    @NotNull
    IConnectionStatusProvider.ConnectionStatus connectionStatus =
        IConnectionStatusProvider.ConnectionStatus.CONNECTED;
    if (connectionStatusProvider != null) {
      connectionStatus = connectionStatusProvider.getConnectionStatus();
    }

    // skip run only if we're certainly disconnected
    if (connectionStatus == IConnectionStatusProvider.ConnectionStatus.DISCONNECTED) {
      return;
    }

    final SendFireAndForget sender = factory.create(hub, options);
    if (sender == null) {
      options.getLogger().log(SentryLevel.ERROR, "SendFireAndForget factory is null.");
      return;
    }

    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                try {
                  sender.send();
                } catch (Throwable e) {
                  options
                      .getLogger()
                      .log(SentryLevel.ERROR, "Failed trying to send cached events.", e);
                }
              });

    } catch (RejectedExecutionException e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Failed to call the executor. Cached events will not be sent. Did you call Sentry.close()?",
              e);
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to call the executor. Cached events will not be sent", e);
    }
  }
}
