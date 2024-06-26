package io.sentry.opentelemetry;

import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.sentry.Sentry;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryOptions;
import io.sentry.SentrySpanFactoryHolder;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryPackage;
import io.sentry.util.SpanUtils;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    final @Nullable VersionInfoHolder versionInfoHolder = createVersionInfo();

    final @NotNull OtelSpanFactory spanFactory = new OtelSpanFactory();
    SentrySpanFactoryHolder.setSpanFactory(spanFactory);
    /**
     * We're currently overriding the storage mechanism to allow for cleanup of non closed OTel
     * scopes. These happen when using e.g. Sentry static API due to getCurrentScopes() invoking
     * Context.makeCurrent and then ignoring the returned lifecycle token (OTel Scope). After fixing
     * the classloader problem (sentry bootstrap dependency is currently in agent classloader) we
     * can revisit and try again to set the storage instead of overriding it in the wrapper. We
     * should try to use OTels StorageProvider mechanism instead.
     */
    //    ContextStorage.addWrapper((storage) -> new SentryContextStorage(storage));
    ContextStorage.addWrapper(
        (storage) -> new SentryContextStorage(new SentryOtelThreadLocalStorage()));

    if (isSentryAutoInitEnabled()) {
      Sentry.init(
          options -> {
            options.setEnableExternalConfiguration(true);
            options.setIgnoredSpanOrigins(SpanUtils.ignoredSpanOriginsForOpenTelemetry());
            options.setSpanFactory(spanFactory);
            final @Nullable SdkVersion sdkVersion = createSdkVersion(options, versionInfoHolder);
            // TODO [POTEL] is detecting a version mismatch between application and agent possible?
            if (sdkVersion != null) {
              options.setSdkVersion(sdkVersion);
            }
          });
    }

    if (versionInfoHolder != null) {
      for (SentryPackage pkg : versionInfoHolder.packages) {
        SentryIntegrationPackageStorage.getInstance().addPackage(pkg.getName(), pkg.getVersion());
      }
      for (String integration : versionInfoHolder.integrations) {
        SentryIntegrationPackageStorage.getInstance().addIntegration(integration);
      }
    }

    autoConfiguration
        .addTracerProviderCustomizer(this::configureSdkTracerProvider)
        .addPropertiesSupplier(this::getDefaultProperties);
  }

  private boolean isSentryAutoInitEnabled() {
    final @Nullable String sentryAutoInit = System.getenv("SENTRY_AUTO_INIT");

    if (sentryAutoInit != null) {
      return "true".equalsIgnoreCase(sentryAutoInit);
    } else {
      final @Nullable String sentryPropertiesFile = System.getenv("SENTRY_PROPERTIES_FILE");
      final @Nullable String sentryDsn = System.getenv("SENTRY_DSN");

      return sentryPropertiesFile != null || sentryDsn != null;
    }
  }

  private @Nullable VersionInfoHolder createVersionInfo() {
    VersionInfoHolder infoHolder = null;
    try {
      final @NotNull Enumeration<URL> resources =
          ClassLoader.getSystemClassLoader().getResources("META-INF/MANIFEST.MF");
      while (resources.hasMoreElements()) {
        try {
          final @NotNull Manifest manifest = new Manifest(resources.nextElement().openStream());
          final @Nullable Attributes mainAttributes = manifest.getMainAttributes();
          if (mainAttributes != null) {
            final @Nullable String name = mainAttributes.getValue("Sentry-Opentelemetry-SDK-Name");
            final @Nullable String version = mainAttributes.getValue("Sentry-Version-Name");

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
          }
        } catch (Exception e) {
          // ignore
        }
      }
    } catch (IOException e) {
      // ignore
    }
    return infoHolder;
  }

  private @Nullable SdkVersion createSdkVersion(
      final @NotNull SentryOptions sentryOptions,
      final @Nullable VersionInfoHolder versionInfoHolder) {
    SdkVersion sdkVersion = sentryOptions.getSdkVersion();

    if (versionInfoHolder != null
        && versionInfoHolder.sdkName != null
        && versionInfoHolder.sdkVersion != null) {
      sdkVersion =
          SdkVersion.updateSdkVersion(
              sdkVersion, versionInfoHolder.sdkName, versionInfoHolder.sdkVersion);
    }
    return sdkVersion;
  }

  private static class VersionInfoHolder {
    private @Nullable String sdkName;
    private @Nullable String sdkVersion;
    private List<SentryPackage> packages = new ArrayList<>();
    private List<String> integrations = new ArrayList<>();
  }

  private SdkTracerProviderBuilder configureSdkTracerProvider(
      SdkTracerProviderBuilder tracerProvider, ConfigProperties config) {
    return tracerProvider
        .setSampler(new SentrySampler())
        .addSpanProcessor(new OtelSentrySpanProcessor())
        .addSpanProcessor(BatchSpanProcessor.builder(new SentrySpanExporter()).build());
  }

  private Map<String, String> getDefaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.propagators", "sentry");
    return properties;
  }
}
