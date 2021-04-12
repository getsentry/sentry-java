package io.sentry.spring.boot;

import io.sentry.SentryOptions;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

public final class SentryTracingCondition extends AnyNestedCondition {

  public SentryTracingCondition() {
    super(ConfigurationPhase.REGISTER_BEAN);
  }

  @ConditionalOnProperty(name = "sentry.enable-tracing", havingValue = "true")
  @SuppressWarnings("UnusedNestedClass")
  private static class SentryTracingEnabled {}

  @ConditionalOnProperty(name = "sentry.traces-sample-rate")
  @SuppressWarnings("UnusedNestedClass")
  private static class SentryTracesSampleRateCondition {}

  @ConditionalOnBean(SentryOptions.TracesSamplerCallback.class)
  @SuppressWarnings("UnusedNestedClass")
  private static class SentryTracesSamplerBeanCondition {}
}
