package io.sentry.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorBuilder;
import io.sentry.InitPriority;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.internal.ManifestVersionReader;
import io.sentry.protocol.SdkVersion;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  private static final String GRAPHQL_OPERATION_NAME_PROPERTY =
      "otel.instrumentation.graphql.operation-name-in-span-name.enabled";
  private static final String DEPRECATED_GRAPHQL_OPERATION_NAME_PROPERTY =
      "otel.instrumentation.graphql.add-operation-name-to-span-name.enabled";

  public static volatile boolean skipInit = false;

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    ensureSentryOtelStorageIsInitialized();
    final @Nullable ManifestVersionReader.VersionInfoHolder versionInfoHolder =
        ManifestVersionReader.getInstance().readOpenTelemetryVersion();

    if (isSentryAutoInitEnabled()) {
      Sentry.init(options -> {
        options.setEnableExternalConfiguration(true);
        options.setInitPriority(InitPriority.HIGH);
        final @Nullable SdkVersion sdkVersion = createSdkVersion(options, versionInfoHolder);
        if (sdkVersion != null) {
          options.setSdkVersion(sdkVersion);
        }
      });
    }

    autoConfiguration
        .addPropertiesSupplier(this::getDefaultProperties)
        .addPropertiesCustomizer(this::customizeOpenTelemetryDefaults)
        .addTracerProviderCustomizer(this::configureSdkTracerProvider);
  }

  private static void ensureSentryOtelStorageIsInitialized() {
    /*
    accessing Sentry.something will cause ScopesStorageFactory to run,
    which causes OtelContextScopesStorage.init to register SentryContextStorage
    as a wrapper. The wrapper can only be set until storage has been initialized by OpenTelemetry.
    */
    Sentry.getGlobalScope();
  }

  Map<String, String> customizeOpenTelemetryDefaults(final @NotNull ConfigProperties config) {
    final @Nullable String configuredValue = config.getString(GRAPHQL_OPERATION_NAME_PROPERTY);
    if (configuredValue != null) {
      return Collections.emptyMap();
    }

    final @Nullable String deprecatedValue =
        config.getString(DEPRECATED_GRAPHQL_OPERATION_NAME_PROPERTY);
    return Collections.singletonMap(
        GRAPHQL_OPERATION_NAME_PROPERTY, deprecatedValue != null ? deprecatedValue : "true");
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
        .addSpanProcessor(createBatchSpanProcessor(config));
  }

  private BatchSpanProcessor createBatchSpanProcessor(ConfigProperties config) {
    final BatchSpanProcessorBuilder builder = BatchSpanProcessor.builder(new SentrySpanExporter());
    final @Nullable Duration scheduleDelay = config.getDuration("otel.bsp.schedule.delay");
    if (scheduleDelay != null) {
      builder.setScheduleDelay(scheduleDelay);
    }
    return builder.build();
  }

  private Map<String, String> getDefaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.propagators", "sentry");
    return properties;
  }
}
