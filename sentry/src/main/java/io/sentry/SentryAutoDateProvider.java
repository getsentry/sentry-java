package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return !isAndroid() && isJavaNinePlus();
  }

  private static boolean isAndroid() {
    try {
      final @Nullable String javaVendor = System.getProperty("java.vendor");
      return javaVendor != null && "The Android Project".equals(javaVendor);
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isJavaNinePlus() {
    try {
      final @Nullable String javaStringVersion = System.getProperty("java.specification.version");
      if (javaStringVersion != null) {
        final @NotNull double javaVersion = Double.valueOf(javaStringVersion);
        return javaVersion >= 9.0;
      } else {
        return false;
      }
    } catch (Exception e) {
      return false;
    }
  }
}
