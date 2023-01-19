package io.sentry.android.core;

import static java.time.temporal.ChronoField.NANO_OF_SECOND;

import android.annotation.SuppressLint;
import io.sentry.SentryDate;
import io.sentry.SentryDateProvider;
import io.sentry.SentryInstantDateProvider;
import io.sentry.SentryNanotimeDateProvider;
import java.time.Instant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This {@link SentryDateProvider} will use {@link java.time.Instant} for Android API 26+ or a
 * combination of {@link java.util.Date} and System.nanoTime() on lower versions.
 */
@ApiStatus.Internal
public final class SentryAndroidDateProvider implements SentryDateProvider {

  private @NotNull SentryDateProvider dateProvider;

  @SuppressLint("NewApi")
  public SentryAndroidDateProvider(final @NotNull BuildInfoProvider buildInfoProvider) {
    try {
      Instant instant = Instant.now();
      instant.get(NANO_OF_SECOND);
      dateProvider = new SentryInstantDateProvider();
    } catch (Throwable t) {
      dateProvider = new SentryNanotimeDateProvider();
    }
  }

  @Override
  public SentryDate now() {
    return dateProvider.now();
  }
}
