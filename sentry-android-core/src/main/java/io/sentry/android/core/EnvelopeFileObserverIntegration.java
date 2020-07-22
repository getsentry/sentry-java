package io.sentry.android.core;

import io.sentry.core.EnvelopeSender;
import io.sentry.core.IHub;
import io.sentry.core.ILogger;
import io.sentry.core.Integration;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.util.Objects;
import java.io.Closeable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Watches the envelope dir. and send them (events) over. */
public abstract class EnvelopeFileObserverIntegration implements Integration, Closeable {
  private @Nullable EnvelopeFileObserver observer;
  private @Nullable ILogger logger;

  public static @NotNull EnvelopeFileObserverIntegration getOutboxFileObserver() {
    return new OutboxEnvelopeFileObserverIntegration();
  }

  @Override
  public final void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
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

      final EnvelopeSender envelopeSender =
          new EnvelopeSender(
              hub,
              options.getEnvelopeReader(),
              options.getSerializer(),
              logger,
              options.getFlushTimeoutMillis());

      observer =
          new EnvelopeFileObserver(path, envelopeSender, logger, options.getFlushTimeoutMillis());
      observer.startWatching();

      logger.log(SentryLevel.DEBUG, "EnvelopeFileObserverIntegration installed.");
    }
  }

  @Override
  public void close() {
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
