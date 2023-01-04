package io.sentry;

import io.sentry.util.Platform;
import org.jetbrains.annotations.NotNull;

public final class SentryAutoDateProvider implements SentryDateProvider {

  private final @NotNull SentryDateProvider dateProvider;

  public SentryAutoDateProvider() {
    if (checkInstantAvailabilityAndPrecision()) {
      dateProvider = new SentryNanotimeDateProvider();
    } else {
      dateProvider = new SentryInstantDateProvider();
    }
  }

  @Override
  public SentryDate now() {
    return dateProvider.now();
  }

  /**
   * Check for Java 9+ because older versions only offer ms precision for {@link java.time.Instant}.
   * This class should not be used for Android as it cannot check Android API level.
   *
   * @return true if Instant offers high precision and this is not Android
   */
  private static boolean checkInstantAvailabilityAndPrecision() {
    return Platform.isJvm() && Platform.isJavaNinePlus();
  }
}
