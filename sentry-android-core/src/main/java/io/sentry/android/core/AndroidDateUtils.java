package io.sentry.android.core;

import io.sentry.NoOpLogger;
import io.sentry.SentryDate;
import io.sentry.SentryDateProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AndroidDateUtils {

  private static final SentryDateProvider dateProvider =
      new SentryAndroidDateProvider(new BuildInfoProvider(NoOpLogger.getInstance()));

  /**
   * Get the current SentryDate (UTC).
   *
   * <p>NOTE: options.getDateProvider() should be preferred. This is only a fallback for static
   * invocations.
   *
   * @return the UTC SentryDate
   */
  public static @NotNull SentryDate getCurrentSentryDateTime() {
    return dateProvider.now();
  }
}
