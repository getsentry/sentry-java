package io.sentry.samples.spring.boot4.otlp;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;
import io.sentry.opentelemetry.otlp.OpenTelemetryOtlpPropagator;

public final class SentryOtlpPropagatorProvider implements ConfigurablePropagatorProvider {
  @Override
  public TextMapPropagator getPropagator(ConfigProperties config) {
    return new OpenTelemetryOtlpPropagator();
  }

  @Override
  public String getName() {
    return "sentry";
  }
}
