package io.sentry.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;

public final class SentryAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addTracerProviderCustomizer(this::configureSdkTracerProvider);
    //      .addPropertiesSupplier(this::getDefaultProperties);
  }

  private SdkTracerProviderBuilder configureSdkTracerProvider(
      SdkTracerProviderBuilder tracerProvider, ConfigProperties config) {

    return tracerProvider.addSpanProcessor(new SentrySpanProcessor());
  }

  //  private Map<String, String> getDefaultProperties() {
  //    Map<String, String> properties = new HashMap<>();
  //    properties.put("otel.exporter.otlp.endpoint", "http://backend:8080");
  //    properties.put("otel.exporter.otlp.insecure", "true");
  //    properties.put("otel.config.max.attrs", "16");
  //    properties.put("otel.traces.sampler", "demo");
  //    return properties;
  //  }
}
