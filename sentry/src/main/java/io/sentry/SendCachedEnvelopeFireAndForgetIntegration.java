package io.sentry;

import io.sentry.cache.EnvelopeCache;
import io.sentry.util.Objects;
import java.io.File;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sends cached events over when your App. is starting. */
public final class SendCachedEnvelopeFireAndForgetIntegration implements Integration {

  private final @NotNull SendFireAndForgetFactory factory;

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

    @Nullable
    String getDirPath();

    default boolean hasValidPath(final @Nullable String dirPath, final @NotNull ILogger logger) {
      if (dirPath == null) {
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

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public final void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    Objects.requireNonNull(options, "SentryOptions is required");

    final String cachedDir = options.getCacheDirPath();
    if (!factory.hasValidPath(cachedDir, options.getLogger())) {
      options.getLogger().log(SentryLevel.ERROR, "No cache dir path is defined in options.");
      return;
    }

    final SendFireAndForget sender = factory.create(hub, options);

    if (sender == null) {
      options.getLogger().log(SentryLevel.ERROR, "SendFireAndForget factory is null.");
      return;
    }

    try {
      Future<?> future = options
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

      final String dirPath = factory.getDirPath();
      if (dirPath != null && EnvelopeCache.hasStartupCrashMarker(dirPath, options)) {
        options
          .getLogger()
          .log(SentryLevel.DEBUG, "Startup Crash marker exists, blocking flush.");
        try {
          future.get(options.getStartupCrashFlushTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
          // TODO: handle more exceptions
          options
            .getLogger()
            .log(SentryLevel.DEBUG, "Synchronous send timed out, continuing in the background.");
        }
      } else {
        options
          .getLogger()
          .log(SentryLevel.DEBUG, "No Startup Crash marker exists, flushing asynchronously.");
      }

      options
          .getLogger()
          .log(SentryLevel.DEBUG, "SendCachedEventFireAndForgetIntegration installed.");
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to call the executor. Cached events will not be sent", e);
    }
  }
}
