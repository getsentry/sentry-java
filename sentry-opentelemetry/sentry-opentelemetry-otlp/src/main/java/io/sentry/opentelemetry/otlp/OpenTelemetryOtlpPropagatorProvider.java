package io.sentry.opentelemetry.otlp;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;

public final class OpenTelemetryOtlpPropagatorProvider implements ConfigurablePropagatorProvider {
  @Override
  public TextMapPropagator getPropagator(ConfigProperties config) {
    return new OpenTelemetryOtlpPropagator();
  }

  @Override
  public String getName() {
    return "sentry";
  }
}
