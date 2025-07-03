package io.sentry.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.sentry.InitPriority;
import io.sentry.Sentry;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryOptions;
import io.sentry.internal.ManifestVersionReader;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryPackage;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  private static final Logger logger = Logger.getLogger(SentryAutoConfigurationCustomizerProvider.class.getName());

  public static volatile boolean skipInit = false;

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    ensureSentryOtelStorageIsInitialized();
    customizeOpenTelemetryDefaults();
    final @Nullable ManifestVersionReader.VersionInfoHolder versionInfoHolder =
        ManifestVersionReader.getInstance().readOpenTelemetryVersion();

    if (isSentryAutoInitEnabled()) {
      Sentry.init(
          options -> {
            options.setEnableExternalConfiguration(true);
            options.setInitPriority(InitPriority.HIGH);
            final @Nullable SdkVersion sdkVersion = createSdkVersion(options, versionInfoHolder);
            if (sdkVersion != null) {
              options.setSdkVersion(sdkVersion);
            }
          });
    }

    if (versionInfoHolder != null) {
      for (SentryPackage pkg : versionInfoHolder.getPackages()) {
        SentryIntegrationPackageStorage.getInstance().addPackage(pkg.getName(), pkg.getVersion());
      }
      for (String integration : versionInfoHolder.getIntegrations()) {
        SentryIntegrationPackageStorage.getInstance().addIntegration(integration);
      }
    }

    autoConfiguration
        .addTracerProviderCustomizer(this::configureSdkTracerProvider)
        .addPropertiesSupplier(this::getDefaultProperties);
  }

  private static void ensureSentryOtelStorageIsInitialized() {
    /*
    accessing Sentry.something will cause ScopesStorageFactory to run,
    which causes OtelContextScopesStorage.init to register SentryContextStorage
    as a wrapper. The wrapper can only be set until storage has been initialized by OpenTelemetry.
    */
    Sentry.getGlobalScope();
  }

  private void customizeOpenTelemetryDefaults() {
    try {
      if (System.getProperty("otel.instrumentation.graphql.add-operation-name-to-span-name.enabled")
          == null) {
        System.setProperty(
            "otel.instrumentation.graphql.add-operation-name-to-span-name.enabled", "true");
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Unable to change OpenTelemetry defaults for use with Sentry.", e);
    }
  }

  private boolean isSentryAutoInitEnabled() {
    if (skipInit) {
      return false;
    }
    final @Nullable String sentryAutoInit = System.getenv("SENTRY_AUTO_INIT");

    if (sentryAutoInit != null) {
      return "true".equalsIgnoreCase(sentryAutoInit);
    } else {
      final @Nullable String sentryPropertiesFile = System.getenv("SENTRY_PROPERTIES_FILE");
      final @Nullable String sentryDsn = System.getenv("SENTRY_DSN");

      return sentryPropertiesFile != null || sentryDsn != null;
    }
  }

  private @Nullable SdkVersion createSdkVersion(
      final @NotNull SentryOptions sentryOptions,
      final @Nullable ManifestVersionReader.VersionInfoHolder versionInfoHolder) {
    SdkVersion sdkVersion = sentryOptions.getSdkVersion();

    if (versionInfoHolder != null
        && versionInfoHolder.getSdkName() != null
        && versionInfoHolder.getSdkVersion() != null) {
      sdkVersion =
          SdkVersion.updateSdkVersion(
              sdkVersion, versionInfoHolder.getSdkName(), versionInfoHolder.getSdkVersion());
    }
    return sdkVersion;
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
