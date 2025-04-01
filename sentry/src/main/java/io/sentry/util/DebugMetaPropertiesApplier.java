package io.sentry.util;

import static io.sentry.SentryLevel.DEBUG;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.util.List;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DebugMetaPropertiesApplier {

  public static @NotNull String DEBUG_META_PROPERTIES_FILENAME = "sentry-debug-meta.properties";

  public static void applyToOptions(
      final @NotNull SentryOptions options, final @Nullable List<Properties> debugMetaProperties) {
    if (debugMetaProperties != null) {
      applyBundleIds(options, debugMetaProperties);
      applyProguardUuid(options, debugMetaProperties);
    }
  }

  private static void applyBundleIds(
      final @NotNull SentryOptions options, final @NotNull List<Properties> debugMetaProperties) {
    if (options.getBundleIds().isEmpty()) {
      for (Properties properties : debugMetaProperties) {
        final @Nullable String bundleIdStrings = properties.getProperty("io.sentry.bundle-ids");
        if (options.getLogger().isEnabled(DEBUG)) {
          options.getLogger().log(SentryLevel.DEBUG, "Bundle IDs found: %s", bundleIdStrings);
        }
        if (bundleIdStrings != null) {
          final @NotNull String[] bundleIds = bundleIdStrings.split(",", -1);
          for (final String bundleId : bundleIds) {
            options.addBundleId(bundleId);
          }
        }
      }
    }
  }

  private static void applyProguardUuid(
      final @NotNull SentryOptions options, final @NotNull List<Properties> debugMetaProperties) {
    if (options.getProguardUuid() == null) {
      for (Properties properties : debugMetaProperties) {
        final @Nullable String proguardUuid = getProguardUuid(properties);
        if (proguardUuid != null) {
          if (options.getLogger().isEnabled(DEBUG)) {
            options.getLogger().log(SentryLevel.DEBUG, "Proguard UUID found: %s", proguardUuid);
          }
          options.setProguardUuid(proguardUuid);
          break;
        }
      }
    }
  }

  public static @Nullable String getProguardUuid(final @NotNull Properties debugMetaProperties) {
    return debugMetaProperties.getProperty("io.sentry.ProguardUuids");
  }
}
