package io.sentry.android.core;

import androidx.annotation.WorkerThread;
import io.sentry.BackfillingEventProcessor;
import io.sentry.Hint;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEvent;
import io.sentry.SentryExceptionFactory;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SentryStackTraceFactory;
import io.sentry.cache.PersistingScopeObserver;
import io.sentry.hints.Backfillable;
import io.sentry.util.HintUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.sentry.cache.PersistingScopeObserver.ENVIRONMENT_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.RELEASE_FILENAME;

/**
 * AnrV2Integration processes events on a background thread, hence the event processors will also be
 * invoked on the same background thread, so we can safely read data from disk synchronously.
 */
@ApiStatus.Internal
@WorkerThread
public final class AnrV2EventProcessor implements BackfillingEventProcessor {

  /**
   * Default value for {@link SentryEvent#getEnvironment()} set when both event and {@link
   * SentryOptions} do not have the environment field set.
   */
  private static final String DEFAULT_ENVIRONMENT = "production";

  private final @NotNull SentryAndroidOptions options;

  private final @NotNull SentryExceptionFactory sentryExceptionFactory;

  public AnrV2EventProcessor(final @NotNull SentryAndroidOptions options) {
    this.options = options;

    final SentryStackTraceFactory sentryStackTraceFactory =
      new SentryStackTraceFactory(
        this.options.getInAppExcludes(), this.options.getInAppIncludes());

    sentryExceptionFactory = new SentryExceptionFactory(sentryStackTraceFactory);
  }

  @Override public @Nullable SentryEvent process(@NotNull SentryEvent event, @NotNull Hint hint) {
    final Object unwrappedHint = HintUtils.getSentrySdkHint(hint);
    if (!(unwrappedHint instanceof Backfillable)) {
      options
        .getLogger()
        .log(SentryLevel.WARNING,
          "The event is not Backfillable, but has been passed to BackfillingEventProcessor, skipping.");
      return event;
    }

    if (!((Backfillable) unwrappedHint).shouldEnrich()) {
      options
        .getLogger()
        .log(SentryLevel.DEBUG,
          "The event is Backfillable, but should not be enriched, skipping.");
      return event;
    }

    setPlatform(event);
    setExceptions(event);
    setRelease(event);
    setEnvironment(event);

    return event;
  }

  private void setPlatform(final @NotNull SentryBaseEvent event) {
    if (event.getPlatform() == null) {
      // this actually means JVM related.
      event.setPlatform(SentryBaseEvent.DEFAULT_PLATFORM);
    }
  }

  private void setExceptions(final @NotNull SentryEvent event) {
    final Throwable throwable = event.getThrowableMechanism();
    if (throwable != null) {
      event.setExceptions(sentryExceptionFactory.getSentryExceptions(throwable));
    }
  }

  private void setRelease(final @NotNull SentryBaseEvent event) {
    if (event.getRelease() == null) {
      final String release =
        PersistingScopeObserver.read(options, RELEASE_FILENAME, String.class);
      event.setRelease(release);
    }
  }

  private void setEnvironment(final @NotNull SentryBaseEvent event) {
    if (event.getEnvironment() == null) {
      final String environment =
        PersistingScopeObserver.read(options, ENVIRONMENT_FILENAME, String.class);
      event.setEnvironment(environment != null ? environment : DEFAULT_ENVIRONMENT);
    }
  }
}
