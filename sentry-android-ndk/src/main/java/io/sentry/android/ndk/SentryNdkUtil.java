package io.sentry.android.ndk;

import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.protocol.SdkVersion;
import org.jetbrains.annotations.Nullable;

/**
 * Util class to make SentryNdk testable, as SentryNdk inits native libraries and it breaks on init.
 */
final class SentryNdkUtil {

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-android-ndk", BuildConfig.VERSION_NAME);
  }

  private SentryNdkUtil() {}

  /**
   * Adds the sentry-android-ndk package into the package list
   *
   * @param sdkVersion the SdkVersion object
   */
  static void addPackage(@Nullable final SdkVersion sdkVersion) {
    if (sdkVersion == null) {
      return;
    }
    sdkVersion.addPackage("maven:io.sentry:sentry-android-ndk", BuildConfig.VERSION_NAME);
  }
}
