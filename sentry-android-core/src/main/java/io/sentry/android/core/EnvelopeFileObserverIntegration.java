package io.sentry.android.core;

import io.sentry.core.EnvelopeSender;
import io.sentry.core.IEnvelopeReader;
import io.sentry.core.IHub;
import io.sentry.core.ILogger;
import io.sentry.core.Integration;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import java.io.Closeable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Watches the envelope dir. and send them (events) over. */
public abstract class EnvelopeFileObserverIntegration implements Integration, Closeable {
  private @Nullable EnvelopeFileObserver observer;
  private final @NotNull IEnvelopeReader envelopeReader;

  EnvelopeFileObserverIntegration(final @NotNull IEnvelopeReader envelopeReader) {
    this.envelopeReader = envelopeReader;
  }

  public static EnvelopeFileObserverIntegration getOutboxFileObserver(
      final @NotNull IEnvelopeReader envelopeReader) {
    return new OutboxEnvelopeFileObserverIntegration(envelopeReader);
  }

  @Override
  public final void register(IHub hub, SentryOptions options) {
    ILogger logger = options.getLogger();
    String path = getPath(options);
    if (path == null) {
      logger.log(
          SentryLevel.WARNING,
          "Null given as a path to EnvelopeFileObserverIntegration. Nothing will be registered.");
    } else {
      logger.log(
          SentryLevel.DEBUG, "Registering EnvelopeFileObserverIntegration for path: %s", path);

      EnvelopeSender envelopeSender =
          new EnvelopeSender(
              hub,
              envelopeReader,
              options.getSerializer(),
              logger,
              options.getFlushTimeoutMillis());

      observer =
          new EnvelopeFileObserver(path, envelopeSender, logger, options.getFlushTimeoutMillis());
      observer.startWatching();

      options.getLogger().log(SentryLevel.DEBUG, "EnvelopeFileObserverIntegration installed.");
    }
  }

  @Override
  public void close() {
    if (observer != null) {
      observer.stopWatching();
    }
  }

  @TestOnly
  abstract String getPath(SentryOptions options);

  private static final class OutboxEnvelopeFileObserverIntegration
      extends EnvelopeFileObserverIntegration {

    OutboxEnvelopeFileObserverIntegration(final @NotNull IEnvelopeReader envelopeReader) {
      super(envelopeReader);
    }

    @Override
    protected String getPath(final SentryOptions options) {
      return options.getOutboxPath();
    }
  }
}
