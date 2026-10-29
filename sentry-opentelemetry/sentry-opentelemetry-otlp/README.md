# sentry-opentelemetry-otlp

This module provides a lightweight integration for using OpenTelemetry alongside the Sentry SDK. It reads trace and span IDs from the OpenTelemetry `Context` so that Sentry events (errors, logs, metrics) are correlated with OpenTelemetry traces.

Unlike the other `sentry-opentelemetry-*` modules, this module does not rely on OpenTelemetry for scope storage or span creation. It is intended for setups where OpenTelemetry handles performance/tracing and Sentry handles errors, logs, metrics, and other products.

Please consult the documentation on how to install and use this integration in the [Sentry Docs for Java](https://docs.sentry.io/platforms/java/).

## Trace propagation

The Sentry propagator only injects tracing headers into outgoing requests when the OpenTelemetry
span contains a URL that matches `tracePropagationTargets`. If no URL can be determined, tracing
headers are not injected.

Messaging producer spans (`SpanKind.PRODUCER`) are an exception because queue and messaging
instrumentations propagate tracing information through message metadata rather than a URL.
