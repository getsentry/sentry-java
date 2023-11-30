package io.sentry.util;

import io.sentry.SentryIntegrationPackageStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class IntegrationUtils {
  public static void addIntegrationToSdkVersion(final @NotNull Class<?> clazz) {
    final String name =
        clazz
            .getSimpleName()
            .replace("Sentry", "")
            .replace("Integration", "")
            .replace("Interceptor", "")
            .replace("EventProcessor", "");
    addIntegrationToSdkVersion(name);
  }

  public static void addIntegrationToSdkVersion(final @NotNull String name) {
    SentryIntegrationPackageStorage.getInstance().addIntegration(name);
  }
}
