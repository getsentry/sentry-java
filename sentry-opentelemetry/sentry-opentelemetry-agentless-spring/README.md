# sentry-opentelemetry-agentless-spring

This module allows the use of Sentry with OpenTelemetry in SpringBoot without an agent by using the OpenTelemetry Spring Boot Starter.
For guidance on when to use this module instead of the agent, please have a look at the [OpenTelemetry Spring Boot Starter documentation](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/).

This module allows the use of Sentry with OpenTelemetry in SpringBoot without an agent by using the OpenTelemetry Spring Boot Starter.
For guidance on when to use this module instead of the agent, please have a look at the [OpenTelemetry Spring Boot Starter documentation](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/).

## How to use it

Add the latest `sentry-opentelemetry-agentless-spring` module as a dependency to your Sentry enabled [SpringBoot](https://docs.sentry.io/platforms/java/guides/spring-boot/) application and add the following to your `application.properties`:

```properties
# OTEL configuration
otel.propagators=tracecontext,baggage,sentry
otel.logs.exporter=none
otel.metrics.exporter=none
otel.traces.exporter=none
```

This module will automatically configure OpenTelemetry and Sentry for you.

With the dependency and configuration in place, just run your SpringBoot application as usual.
