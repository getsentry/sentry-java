package io.sentry;

/**
 * Configures the SDK to either automatically determine if OpenTelemetry is available, whether to
 * use it and what way to use it in.
 */
public enum SentryOpenTelemetryMode {
  /** Let the SDK figure out what mode OpenTelemetry is in and whether to even use OpenTelemetry */
  AUTO,
  /**
   * For now this only means no span origins will be ignored. This does however not mean, the SDK
   * won't try tro use OpenTelemetry if available.
   *
   * <p>Due to some parts of the SDK being initialized before any config mechanism is available, we
   * cannot completely disable the OpenTelemetry parts with this setting.
   */
  ALL_ORIGINS,
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
