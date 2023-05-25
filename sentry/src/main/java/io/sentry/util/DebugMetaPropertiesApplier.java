package io.sentry.util;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DebugMetaPropertiesApplier {

  public static @NotNull String DEBUG_META_PROPERTIES_FILENAME = "sentry-debug-meta.properties";

  public static void applyToOptions(
      final @NotNull SentryOptions options, final @Nullable Properties debugMetaProperties) {
    if (debugMetaProperties != null) {
      applyProguardUuid(options, debugMetaProperties);
      applyBundleIds(options, debugMetaProperties);
    }
  }

  private static void applyBundleIds(
      final @NotNull SentryOptions options, final @NotNull Properties debugMetaProperties) {
    if (options.getBundleIds().isEmpty()) {
      final @Nullable String bundleIdStrings =
          debugMetaProperties.getProperty("io.sentry.bundle-ids");
      options.getLogger().log(SentryLevel.DEBUG, "Bundle IDs found: %s", bundleIdStrings);
      if (bundleIdStrings != null) {
        final @NotNull String[] bundleIds = bundleIdStrings.split(",", -1);
        for (final String bundleId : bundleIds) {
          options.addBundleId(bundleId);
        }
      }
    }
  }

  private static void applyProguardUuid(
      final @NotNull SentryOptions options, final @NotNull Properties debugMetaProperties) {
    if (options.getProguardUuid() == null) {
      final @Nullable String proguardUuid =
          debugMetaProperties.getProperty("io.sentry.ProguardUuids");
      options.getLogger().log(SentryLevel.DEBUG, "Proguard UUID found: %s", proguardUuid);
      options.setProguardUuid(proguardUuid);
    }
  }
}
