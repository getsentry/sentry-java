package io.sentry.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import java.util.HashMap;
import java.util.Map;

public final class SentryAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration
        .addTracerProviderCustomizer(this::configureSdkTracerProvider)
        .addPropertiesSupplier(this::getDefaultProperties);
  }

  private SdkTracerProviderBuilder configureSdkTracerProvider(
      SdkTracerProviderBuilder tracerProvider, ConfigProperties config) {
    return tracerProvider.addSpanProcessor(new SentrySpanProcessor());
  }

  private Map<String, String> getDefaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.traces.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.propagators", "sentry");
    return properties;
  }
}
