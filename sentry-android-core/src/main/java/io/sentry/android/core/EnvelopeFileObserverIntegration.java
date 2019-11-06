package io.sentry.android.core;

import io.sentry.core.*;
import java.io.Closeable;
import java.io.File;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

abstract class EnvelopeFileObserverIntegration implements Integration, Closeable {
  private @Nullable EnvelopeFileObserver observer;

  protected EnvelopeFileObserverIntegration() {}

  @Override
  public void register(IHub hub, SentryOptions options) {
    ILogger logger = options.getLogger();
    String path = getPath(options);
    if (path == null) {
      logger.log(SentryLevel.WARNING, "Null given as a path to %s. Nothing will be registered.");
    } else {
      logger.log(SentryLevel.DEBUG, "Registering CachedEventReaderIntegration for path: %s", path);

      EnvelopeSender envelopeSender =
          new EnvelopeSender(
              hub, new io.sentry.core.EnvelopeReader(), options.getSerializer(), logger);

      observer = new EnvelopeFileObserver(path, envelopeSender, logger);
      observer.startWatching();
    }
  }

  @Override
  public void close() {
    if (observer != null) {
      observer.stopWatching();
    }
  }

  public static EnvelopeFileObserverIntegration getOutboxFileObserver() {
    return new OutboxEnvelopeFileObserverIntegration();
  }

  public static EnvelopeFileObserverIntegration getCachedEnvelopeFileObserver() {
    return new JavaCachedEnvelopeFileObserverIntegration();
  }

  @TestOnly
  abstract String getPath(SentryOptions options);

  private static final class OutboxEnvelopeFileObserverIntegration
      extends EnvelopeFileObserverIntegration {
    @Override
    protected String getPath(final SentryOptions options) {
      return options.getOutboxPath();
    }
  }

  private static final class JavaCachedEnvelopeFileObserverIntegration
      extends EnvelopeFileObserverIntegration {
    @Override
    protected String getPath(final SentryOptions options) {
      // TODO: Wherever we're caching events from the Java layer
      return options.getCacheDirPath() + File.separator + "cached";
    }
  }
}
