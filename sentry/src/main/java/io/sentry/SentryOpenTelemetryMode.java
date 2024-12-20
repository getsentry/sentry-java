package io.sentry;

/**
 * Configures the SDK to either automatically determine if OpenTelemetry is available, whether to
 * use it and what way to use it in.
 */
public enum SentryOpenTelemetryMode {
  /**
   * Let the SDK figure out what mode OpenTelemetry is in and whether to even use OpenTelemetry This
   * is the default for non Android.
   */
  AUTO,
  /** Do not try to use OpenTelemetry, even if it is available. This is the default for Android. */
  OFF,
  /** The `sentry-opentelemetry-agent` is used */
  AGENT,
  /**
   * `sentry-opentelemetry-agentless` is used, meaning OpenTelemetry will be used but there is no
   * auto instrumentation available.
   */
  AGENTLESS,
  /**
   * `sentry-opentelemetry-agentless-spring-boot` is used, meaning
   * `opentelemetry-spring-boot-starter` and its auto instrumentation is used.
   */
  AGENTLESS_SPRING
}
