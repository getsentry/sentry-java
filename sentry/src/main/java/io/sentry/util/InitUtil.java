package io.sentry.util;

import io.sentry.SentryIntegrationPackageStorage;
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
    if (SentryIntegrationPackageStorage.getInstance()
        .checkForMixedVersions(newOptions.getLogger())) {
      newOptions
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Not initializing Sentry because mixed SDK versions have been detected.");
      return false;
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
