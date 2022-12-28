package io.sentry.samples.jul;

import io.sentry.Instrumenter;
import io.sentry.SentryOptions;
import io.sentry.SentryOptionsCustomizer;

public class MyOptionsCustomizer implements SentryOptionsCustomizer {
  @Override
  public void configure(SentryOptions options) {
    options.setInstrumenter(Instrumenter.OTEL);
    options.setEnvironment("options customizer works");
  }
}
