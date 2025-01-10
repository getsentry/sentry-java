package io.sentry.android.core;

import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.Integration;
import io.sentry.OutboxSender;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.io.Closeable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Watches the envelope dir. and send them (events) over. */
public abstract class EnvelopeFileObserverIntegration implements Integration, Closeable {
  private @Nullable EnvelopeFileObserver observer;
  private @Nullable ILogger logger;
  private boolean isClosed = false;
  protected final @NotNull AutoClosableReentrantLock startLock = new AutoClosableReentrantLock();

  public static @NotNull EnvelopeFileObserverIntegration getOutboxFileObserver() {
    return new OutboxEnvelopeFileObserverIntegration();
  }

  @Override
  public final void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    Objects.requireNonNull(scopes, "Scopes are required");
    Objects.requireNonNull(options, "SentryOptions is required");

    logger = options.getLogger();

    final String path = getPath(options);
    if (path == null) {
      logger.log(
          SentryLevel.WARNING,
          "Null given as a path to EnvelopeFileObserverIntegration. Nothing will be registered.");
    } else {
      logger.log(
          SentryLevel.DEBUG, "Registering EnvelopeFileObserverIntegration for path: %s", path);

      try {
        options
            .getExecutorService()
            .submit(
                () -> {
                  try (final @NotNull ISentryLifecycleToken ignored = startLock.acquire()) {
                    if (!isClosed) {
                      startOutboxSender(scopes, options, path);
                    }
                  }
                });
      } catch (Throwable e) {
        logger.log(
            SentryLevel.DEBUG,
            "Failed to start EnvelopeFileObserverIntegration on executor thread.",
            e);
      }
    }
  }

  private void startOutboxSender(
      final @NotNull IScopes scopes,
      final @NotNull SentryOptions options,
      final @NotNull String path) {
    final OutboxSender outboxSender =
        new OutboxSender(
            scopes,
            options.getEnvelopeReader(),
            options.getSerializer(),
            options.getLogger(),
            options.getFlushTimeoutMillis(),
            options.getMaxQueueSize());

    observer =
        new EnvelopeFileObserver(
            path, outboxSender, options.getLogger(), options.getFlushTimeoutMillis());
    try {
      observer.startWatching();
      options.getLogger().log(SentryLevel.DEBUG, "EnvelopeFileObserverIntegration installed.");
    } catch (Throwable e) {
      // it could throw eg NoSuchFileException or NullPointerException
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to initialize EnvelopeFileObserverIntegration.", e);
    }
  }

  @Override
  public void close() {
    try (final @NotNull ISentryLifecycleToken ignored = startLock.acquire()) {
      isClosed = true;
    }
    if (observer != null) {
      observer.stopWatching();

      if (logger != null) {
        logger.log(SentryLevel.DEBUG, "EnvelopeFileObserverIntegration removed.");
      }
    }
  }

  @TestOnly
  abstract @Nullable String getPath(final @NotNull SentryOptions options);

  private static final class OutboxEnvelopeFileObserverIntegration
      extends EnvelopeFileObserverIntegration {

    @Override
    protected @Nullable String getPath(final @NotNull SentryOptions options) {
      return options.getOutboxPath();
    }
  }
}
