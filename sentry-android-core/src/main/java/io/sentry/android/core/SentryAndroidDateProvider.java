package io.sentry.android.core;

import io.sentry.SentryDate;
import io.sentry.SentryDateProvider;
import io.sentry.SentryNanotimeDateProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This {@link SentryDateProvider} will use a combination of {@link java.util.Date} and
 * System.nanoTime() on Android. During testing we discovered that {@link java.time.Instant} only
 * offers ms precision if the build has desugaring enabled. Event without desugaring only API 33
 * offered higher precision. More info can be found here:
 * https://github.com/getsentry/sentry-java/pull/2451
 */
@ApiStatus.Internal
public final class SentryAndroidDateProvider implements SentryDateProvider {

  private @NotNull SentryDateProvider dateProvider = new SentryNanotimeDateProvider();

  @Override
  public SentryDate now() {
    return dateProvider.now();
  }
}
