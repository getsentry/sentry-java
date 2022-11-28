# sentry-opentelemetry

*NOTE: Our OpenTelemetry modules are still experimental. Any feedback is welcome.*

## OpenTelemetry

More information on OpenTelemetry can be found on their [website](https://opentelemetry.io/) as well
as their docs and GitHub repos:
- https://opentelemetry.io/docs/instrumentation/java/getting-started/
- https://github.com/open-telemetry/opentelemetry-java
- https://github.com/open-telemetry/opentelemetry-java-instrumentation

## Modules

### [`sentry-opentelemetry-agent`](sentry-opentelemetry-agent/README.md)

Produces the Sentry OpenTelemetry Java Agent `JAR` that can be used to auto instrument an 
application. Please see the module [README](sentry-opentelemetry-agent/README.md) for more details on how to use it.

### `sentry-opentelemetry-agentcustomization`

This contains customizations to the OpenTelemetry Java Agent such as registering the
`SentrySpanProcessor` and `SentryPropagator` as well as providing default properties that
enable the `sentry` propagator and disable exporters so our agent doesn't trigger lots of log 
warnings due to OTLP server not being there.

### `sentry-opentelemetry-core`

Contains `SentrySpanProcessor` and `SentryPropagator` which are used by our Java Agent but can also
be used when manually instrumenting using OpenTelemetry.
