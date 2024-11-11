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
warnings due to OTLP server not being there. This can also be used without the agent.

### `sentry-opentelemetry-bootstrap`

Classes that are loaded into the bootstrap classloader
(represented as `null` when invoking X.class.classLoader)
These are shared between the agent and the application and include things like storage,
utils, factory, tokens etc.

If you want to use Sentry with OpenTelemetry without the agent,
you also need this module as a dependency.

### `sentry-opentelemetry-core`

Contains `SentrySpanProcessor` and `SentryPropagator` which are used by our Java Agent but can also
be used when manually instrumenting using OpenTelemetry. If you want to use OpenTelemetry without
the agent but still want some configuration convenience, you should rather use the
`sentry-opentelemetry-agentcustomization` module.
