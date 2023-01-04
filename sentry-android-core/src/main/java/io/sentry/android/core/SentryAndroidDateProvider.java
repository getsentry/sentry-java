package io.sentry.android.core;

import io.sentry.SentryDate;
import io.sentry.SentryDateProvider;
import io.sentry.SentryNanotimeDateProvider;
import org.jetbrains.annotations.NotNull;

/**
 * This {@link SentryDateProvider} will use {@link java.time.Instant} for Android API 26+ or a
 * combination of {@link java.util.Date} and System.nanoTime() on lower versions.
 */
public final class SentryAndroidDateProvider implements SentryDateProvider {

  private final @NotNull SentryDateProvider dateProvider;

  public SentryAndroidDateProvider() {
    // TODO can only allow Instant if ActivityLifecycleIntegration /
    // DateUtils.getCurrentSentryDateTime also provides the same date
    //    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    //      dateProvider = new SentryInstantDateProvider();
    //    } else {
    dateProvider = new SentryNanotimeDateProvider();
    //    }
  }

  @Override
  public SentryDate now() {
    return dateProvider.now();
  }
}
