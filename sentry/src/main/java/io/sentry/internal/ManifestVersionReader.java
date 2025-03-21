package io.sentry.internal;

import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.protocol.SentryPackage;
import io.sentry.util.AutoClosableReentrantLock;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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
  private volatile @Nullable VersionInfoHolder versionInfo = null;
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
    return versionInfo;
  }

  public void readManifestFiles() {
    if (hasManifestBeenRead) {
      return;
    }

    @Nullable VersionInfoHolder infoHolder = null;
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
            final @Nullable String version = mainAttributes.getValue("Sentry-Version-Name");
            final @Nullable String sdkName = mainAttributes.getValue("Sentry-SDK-Name");
            final @Nullable String packageName = mainAttributes.getValue("Sentry-SDK-Package-Name");

            if (name != null && version != null) {
              infoHolder = new VersionInfoHolder();
              infoHolder.sdkName = name;
              infoHolder.sdkVersion = version;
              infoHolder.packages.add(
                  new SentryPackage("maven:io.sentry:sentry-opentelemetry-agent", version));
              final @Nullable String otelVersion =
                  mainAttributes.getValue("Sentry-Opentelemetry-Version-Name");
              if (otelVersion != null) {
                infoHolder.packages.add(
                    new SentryPackage("maven:io.opentelemetry:opentelemetry-sdk", otelVersion));
                infoHolder.integrations.add("OpenTelemetry");
              }
              final @Nullable String otelJavaagentVersion =
                  mainAttributes.getValue("Sentry-Opentelemetry-Javaagent-Version-Name");
              if (otelJavaagentVersion != null) {
                infoHolder.packages.add(
                    new SentryPackage(
                        "maven:io.opentelemetry.javaagent:opentelemetry-javaagent",
                        otelJavaagentVersion));
                infoHolder.integrations.add("OpenTelemetry-Agent");
              }
              break;
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
      versionInfo = infoHolder;
    }
  }

  public final static class VersionInfoHolder {
    private @Nullable String sdkName;
    private @Nullable String sdkVersion;
    private List<SentryPackage> packages = new ArrayList<>();
    private List<String> integrations = new ArrayList<>();

    public @Nullable String getSdkName() {
      return sdkName;
    }

    public @Nullable String getSdkVersion() {
      return sdkVersion;
    }

    public List<SentryPackage> getPackages() {
      return packages;
    }

    public List<String> getIntegrations() {
      return integrations;
    }
  }
}
