package io.sentry.util;

import io.sentry.ManifestVersionDetector;
import io.sentry.NoopVersionDetector;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class InitUtil {
  public static boolean shouldInit(
      final @Nullable SentryOptions previousOptions,
      final @NotNull SentryOptions newOptions,
      final boolean isEnabled) {
    if (Platform.isJvm() && newOptions.getVersionDetector() instanceof NoopVersionDetector) {
      newOptions.setVersionDetector(new ManifestVersionDetector(newOptions));
    }
    if (newOptions.getVersionDetector().checkForMixedVersions()) {
      newOptions
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Not initializing Sentry because mixed SDK versions have been detected.");
      final @NotNull String docsUrl =
          Platform.isAndroid()
              ? "https://docs.sentry.io/platforms/android/troubleshooting/mixed-versions"
              : "https://docs.sentry.io/platforms/java/troubleshooting/mixed-versions";
      throw new IllegalStateException(
          "Sentry SDK has detected a mix of versions. This is not supported and likely leads to crashes. Please always use the same version of all Java SDK modules (dependencies). See "
              + docsUrl
              + " for more details.");
    }
    if (!isEnabled) {
      return true;
    }

    if (previousOptions == null) {
      return true;
    }

    if (newOptions.isForceInit()) {
      return true;
    }

    return previousOptions.getInitPriority().ordinal() <= newOptions.getInitPriority().ordinal();
  }
}
