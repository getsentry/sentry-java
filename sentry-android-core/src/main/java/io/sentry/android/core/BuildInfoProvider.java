package io.sentry.android.core;

import static io.sentry.SentryLevel.ERROR;

import android.os.Build;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The Android Impl. of BuildInfoProvider which returns the Build class info. */
@ApiStatus.Internal
public final class BuildInfoProvider {

  final @NotNull ILogger logger;

  public BuildInfoProvider(final @NotNull ILogger logger) {
    this.logger = Objects.requireNonNull(logger, "The ILogger object is required.");
  }
  /**
   * Returns the Build.VERSION.SDK_INT
   *
   * @return the Build.VERSION.SDK_INT
   */
  public int getSdkInfoVersion() {
    return Build.VERSION.SDK_INT;
  }

  public @Nullable String getBuildTags() {
    return Build.TAGS;
  }

  public @Nullable String getManufacturer() {
    return Build.MANUFACTURER;
  }

  public @Nullable String getModel() {
    return Build.MODEL;
  }

  public @Nullable String getVersionRelease() {
    return Build.VERSION.RELEASE;
  }

  /**
   * Check whether the application is running in an emulator.
   * https://github.com/flutter/plugins/blob/master/packages/device_info/android/src/main/java/io/flutter/plugins/deviceinfo/DeviceInfoPlugin.java#L105
   *
   * @return true if the application is running in an emulator, false otherwise
   */
  public @Nullable Boolean isEmulator() {
    try {
      return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
          || Build.FINGERPRINT.startsWith("generic")
          || Build.FINGERPRINT.startsWith("unknown")
          || Build.HARDWARE.contains("goldfish")
          || Build.HARDWARE.contains("ranchu")
          || Build.MODEL.contains("google_sdk")
          || Build.MODEL.contains("Emulator")
          || Build.MODEL.contains("Android SDK built for x86")
          || Build.MANUFACTURER.contains("Genymotion")
          || Build.PRODUCT.contains("sdk_google")
          || Build.PRODUCT.contains("google_sdk")
          || Build.PRODUCT.contains("sdk")
          || Build.PRODUCT.contains("sdk_x86")
          || Build.PRODUCT.contains("vbox86p")
          || Build.PRODUCT.contains("emulator")
          || Build.PRODUCT.contains("simulator");
    } catch (Throwable e) {
      if (logger.isEnabled(ERROR)) {
        logger.log(
            SentryLevel.ERROR, "Error checking whether application is running in an emulator.", e);
      }
      return null;
    }
  }
}
