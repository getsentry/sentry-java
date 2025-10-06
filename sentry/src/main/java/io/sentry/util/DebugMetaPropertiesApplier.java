package io.sentry.util;

import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.util.List;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DebugMetaPropertiesApplier {

  public static @NotNull String DEBUG_META_PROPERTIES_FILENAME = "sentry-debug-meta.properties";

  public static void apply(
      final @NotNull SentryOptions options, final @Nullable List<Properties> debugMetaProperties) {
    if (debugMetaProperties != null) {
      applyToOptions(options, debugMetaProperties);
      applyBuildTool(options, debugMetaProperties);
      applyDistributionOptions(options, debugMetaProperties);
    }
  }

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
        options.getLogger().log(SentryLevel.DEBUG, "Bundle IDs found: %s", bundleIdStrings);
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
          options.getLogger().log(SentryLevel.DEBUG, "Proguard UUID found: %s", proguardUuid);
          options.setProguardUuid(proguardUuid);
          break;
        }
      }
    }
  }

  private static void applyBuildTool(
      final @NotNull SentryOptions options, @NotNull List<Properties> debugMetaProperties) {
    for (Properties properties : debugMetaProperties) {
      final @Nullable String buildTool = getBuildTool(properties);
      if (buildTool != null) {
        @Nullable String buildToolVersion = getBuildToolVersion(properties);
        if (buildToolVersion == null) {
          buildToolVersion = "unknown";
        }
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG, "Build tool found: %s, version %s", buildTool, buildToolVersion);
        SentryIntegrationPackageStorage.getInstance().addPackage(buildTool, buildToolVersion);
        break;
      }
    }
  }

  public static @Nullable String getProguardUuid(final @NotNull Properties debugMetaProperties) {
    return debugMetaProperties.getProperty("io.sentry.ProguardUuids");
  }

  public static @Nullable String getBuildTool(final @NotNull Properties debugMetaProperties) {
    return debugMetaProperties.getProperty("io.sentry.build-tool");
  }

  public static @Nullable String getBuildToolVersion(
      final @NotNull Properties debugMetaProperties) {
    return debugMetaProperties.getProperty("io.sentry.build-tool-version");
  }

  private static void applyDistributionOptions(
      final @NotNull SentryOptions options, final @NotNull List<Properties> debugMetaProperties) {
    for (Properties properties : debugMetaProperties) {
      final @Nullable String orgSlug = getDistributionOrgSlug(properties);
      final @Nullable String projectSlug = getDistributionProjectSlug(properties);
      final @Nullable String orgAuthToken = getDistributionOrgAuthToken(properties);
      final @Nullable String buildConfiguration = getDistributionBuildConfiguration(properties);

      if (orgSlug != null || projectSlug != null || orgAuthToken != null) {
        final @NotNull SentryOptions.DistributionOptions distributionOptions =
            options.getDistribution();

        if (orgSlug != null && distributionOptions.orgSlug.isEmpty()) {
          options.getLogger().log(SentryLevel.DEBUG, "Distribution org slug found: %s", orgSlug);
          distributionOptions.orgSlug = orgSlug;
        }

        if (projectSlug != null && distributionOptions.projectSlug.isEmpty()) {
          options
              .getLogger()
              .log(SentryLevel.DEBUG, "Distribution project slug found: %s", projectSlug);
          distributionOptions.projectSlug = projectSlug;
        }

        if (orgAuthToken != null && distributionOptions.orgAuthToken.isEmpty()) {
          options.getLogger().log(SentryLevel.DEBUG, "Distribution org auth token found");
          distributionOptions.orgAuthToken = orgAuthToken;
        }

        if (buildConfiguration != null && distributionOptions.buildConfiguration == null) {
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Distribution build configuration found: %s",
                  buildConfiguration);
          distributionOptions.buildConfiguration = buildConfiguration;
        }

        break;
      }
    }
  }

  private static @Nullable String getDistributionOrgSlug(
      final @NotNull Properties debugMetaProperties) {
    return debugMetaProperties.getProperty("io.sentry.distribution.org-slug");
  }

  private static @Nullable String getDistributionProjectSlug(
      final @NotNull Properties debugMetaProperties) {
    return debugMetaProperties.getProperty("io.sentry.distribution.project-slug");
  }

  private static @Nullable String getDistributionOrgAuthToken(
      final @NotNull Properties debugMetaProperties) {
    return debugMetaProperties.getProperty("io.sentry.distribution.org-auth-token");
  }

  private static @Nullable String getDistributionBuildConfiguration(
      final @NotNull Properties debugMetaProperties) {
    return debugMetaProperties.getProperty("io.sentry.distribution.build-configuration");
  }
}
