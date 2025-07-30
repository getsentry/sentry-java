package io.sentry.internal;

import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.util.AutoClosableReentrantLock;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ManifestVersionReader {
  private static volatile @Nullable ManifestVersionReader INSTANCE;
  private static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();
  private volatile boolean hasManifestBeenRead = false;
  private final @NotNull VersionInfoHolder versionInfo = new VersionInfoHolder();
  private @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public static @NotNull ManifestVersionReader getInstance() {
    if (INSTANCE == null) {
      try (final @NotNull ISentryLifecycleToken ignored = staticLock.acquire()) {
        if (INSTANCE == null) {
          INSTANCE = new ManifestVersionReader();
        }
      }
    }

    return INSTANCE;
  }

  private ManifestVersionReader() {}

  public @Nullable VersionInfoHolder readOpenTelemetryVersion() {
    readManifestFiles();
    if (versionInfo.sdkVersion == null) {
      return null;
    }
    return versionInfo;
  }

  public void readManifestFiles() {
    if (hasManifestBeenRead) {
      return;
    }

    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (hasManifestBeenRead) {
        return;
      }
      final @NotNull Enumeration<URL> resources =
          ClassLoader.getSystemClassLoader().getResources("META-INF/MANIFEST.MF");
      while (resources.hasMoreElements()) {
        try {
          final @NotNull Manifest manifest = new Manifest(resources.nextElement().openStream());
          final @Nullable Attributes mainAttributes = manifest.getMainAttributes();
          if (mainAttributes != null) {
            final @Nullable String name = mainAttributes.getValue("Sentry-Opentelemetry-SDK-Name");
            final @Nullable String version = mainAttributes.getValue("Implementation-Version");
            final @Nullable String sdkName = mainAttributes.getValue("Sentry-SDK-Name");
            final @Nullable String packageName = mainAttributes.getValue("Sentry-SDK-Package-Name");

            if (name != null && version != null) {
              versionInfo.sdkName = name;
              versionInfo.sdkVersion = version;
              final @Nullable String otelVersion =
                  mainAttributes.getValue("Sentry-Opentelemetry-Version-Name");
              if (otelVersion != null) {
                SentryIntegrationPackageStorage.getInstance()
                    .addPackage("maven:io.opentelemetry:opentelemetry-sdk", otelVersion);
                SentryIntegrationPackageStorage.getInstance().addIntegration("OpenTelemetry");
              }
              final @Nullable String otelJavaagentVersion =
                  mainAttributes.getValue("Sentry-Opentelemetry-Javaagent-Version-Name");
              if (otelJavaagentVersion != null) {
                SentryIntegrationPackageStorage.getInstance()
                    .addPackage(
                        "maven:io.opentelemetry.javaagent:opentelemetry-javaagent",
                        otelJavaagentVersion);
                SentryIntegrationPackageStorage.getInstance().addIntegration("OpenTelemetry-Agent");
              }
              if (name.equals("sentry.java.opentelemetry.agentless")) {
                SentryIntegrationPackageStorage.getInstance()
                    .addIntegration("OpenTelemetry-Agentless");
              }
              if (name.equals("sentry.java.opentelemetry.agentless-spring")) {
                SentryIntegrationPackageStorage.getInstance()
                    .addIntegration("OpenTelemetry-Agentless-Spring");
              }
            }

            if (sdkName != null
                && version != null
                && packageName != null
                && sdkName.startsWith("sentry.java")) {
              SentryIntegrationPackageStorage.getInstance().addPackage(packageName, version);
            }
          }
        } catch (Exception e) {
          // ignore
        }
      }
    } catch (IOException e) {
      // ignore
    } finally {
      hasManifestBeenRead = true;
    }
  }

  public static final class VersionInfoHolder {
    private volatile @Nullable String sdkName;
    private volatile @Nullable String sdkVersion;

    public @Nullable String getSdkName() {
      return sdkName;
    }

    public @Nullable String getSdkVersion() {
      return sdkVersion;
    }
  }
}
