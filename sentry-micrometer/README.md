# sentry-micrometer

This module forwards Micrometer metrics to Sentry while preserving normal Micrometer registry behavior.
It can be used alongside Prometheus, Datadog, OTLP, and other registries.

## Install

Add the Sentry Micrometer module and Micrometer Core:

```kotlin
dependencies {
  implementation("io.sentry:sentry-micrometer:<version>")
  implementation("io.micrometer:micrometer-core:<version>")
}
```

Create a `SentryMeterRegistry` and add it to Micrometer:

```java
SentryMeterRegistry sentryRegistry = new SentryMeterRegistry();
Metrics.addRegistry(sentryRegistry);
```

### Spring Boot

Spring Boot 2, 3, and 4 applications can use auto-configuration by adding `sentry-micrometer`
alongside the matching Sentry Spring Boot starter. The starter does not install this module
transitively.

```kotlin
dependencies {
  implementation("io.sentry:sentry-spring-boot-4-starter:<version>")
  // Spring Boot 3: implementation("io.sentry:sentry-spring-boot-starter-jakarta:<version>")
  // Spring Boot 2: implementation("io.sentry:sentry-spring-boot-starter:<version>")

  implementation("io.sentry:sentry-micrometer:<version>")
}
```

Enable the integration explicitly and optionally configure passive polling:

```properties
sentry.micrometer.enabled=true
sentry.micrometer.poll-interval-millis=60000
```

Auto-configuration requires Sentry to be initialized and backs off when the application provides
its own `SentryMeterRegistry` bean. Spring Boot adds the registry to its primary composite registry,
applies compatible `MeterRegistryCustomizer` beans, and closes it with the application context.
Supported metrics registered automatically by Spring Boot Actuator—including HTTP server, JVM,
and process metrics—are forwarded through the same registry. Sentry's Spring Boot auto-configuration
ignores Logback and Log4j2 logging counters by default; see [Filtering and volume](#filtering-and-volume).
Set the polling interval to zero to keep immediate forwarding enabled without a polling worker;
passive auto-generated meters then remain registered but are not sent.

## Metric mappings

Active meters are forwarded when they are recorded:

| Micrometer meter | Sentry metric |
| --- | --- |
| `Counter` | Counter increment |
| `Timer` | Distribution in milliseconds |
| `DistributionSummary` | Distribution |

Passive meters are polled every 60 seconds by default:

| Micrometer meter | Sentry metric |
| --- | --- |
| `Gauge` | Gauge |
| `TimeGauge` | Gauge in milliseconds |
| `LongTaskTimer` active tasks | `${name}.active` gauge |
| `LongTaskTimer` active duration | `${name}.duration` gauge in milliseconds |
| `FunctionCounter` | Positive counter delta |
| `FunctionTimer` count | `${name}.count` positive counter delta |
| `FunctionTimer` total time | `${name}.total_time` positive counter delta in milliseconds |

The first successful finite function-meter poll establishes its baseline and emits nothing. Later
positive deltas are sent. A decreasing value is treated as a reset and establishes a new baseline.
`FunctionTimer` tracks its count and total-time baselines independently.

A `FunctionTimer` exposes only cumulative count and total time, not individual duration
observations. The integration therefore exports these values as counter deltas rather than a mean
gauge or distribution. Percentiles cannot be reconstructed from count and total time alone.

Unsupported custom meters remain readable through Micrometer but are not exported to Sentry.

## Polling

Pass the interval in milliseconds to configure passive polling:

```java
SentryMeterRegistry sentryRegistry = new SentryMeterRegistry(30_000);
```

Use zero to disable passive polling while keeping active meter forwarding enabled:

```java
SentryMeterRegistry sentryRegistry = new SentryMeterRegistry(0);
```

Each registry with polling enabled owns one daemon scheduler thread. A slow passive-meter callback
delays other passive meters in that registry. Callback failures and non-finite values are skipped
without stopping later meters from being polled.

Active metrics use the Sentry scope and trace context present when they are recorded. Passive
metrics use the context available on the polling thread because Micrometer does not retain the
context that changed a backing value.

## Filtering and volume

Each counter increment, timer recording, or distribution-summary recording creates one Sentry
metric before the metrics batch processor batches it for transport. This includes each log event
counted by Micrometer's Logback or Log4j2 binders. High logging volume can fill the shared metrics
queue and cause other metrics to be dropped.

Outside Spring Boot, no metric names are ignored by default. Configure ignored names before
registering meters:

```java
options.getMetrics().setIgnoredMetrics(Arrays.asList("logback[.]events", "log4j2[.]events"));
```

Sentry's Spring Boot 2, 3, and 4 auto-configuration defaults to `logback[.]events` and
`log4j2[.]events` when the ignored-metrics list is unset. These patterns match the logging counters
`logback.events` and `log4j2.events` without treating the dots as regex wildcards. This filters only
metrics, not actual log messages, Sentry Logs, breadcrumbs, or error events.

An explicit list replaces these defaults. Include them to retain logging exclusions alongside
custom filters:

```properties
sentry.metrics.ignored-metrics=logback[.]events,log4j2[.]events,my.noisy.metric
```

To disable all name filters, including the logging defaults:

```properties
sentry.metrics.ignored-metrics=
```

Defaults are applied before `Sentry.OptionsConfiguration` callbacks. A callback can append filters
with `options.getMetrics().addIgnoredMetric(...)`, replace them with `setIgnoredMetrics(...)`, or
clear them with an empty list or `null`. Enabled external configuration is merged afterward.
These Boot defaults apply only when Micrometer export is enabled. While enabled, manually recorded
Sentry metrics with the same names are also filtered.

For `sentry.properties`, use `metrics.ignored-metrics`; the environment variable is
`SENTRY_METRICS_IGNORED_METRICS`. Android supports the manifest metadata key
`io.sentry.metrics.ignored-metrics` with a comma-separated string value.

Patterns match final exported Sentry names after Micrometer naming conventions, using
case-insensitive exact matches or full regular-expression matches. Derived names such as
`task.active`, `task.duration`, `task.count`, and `task.total_time` are matched individually.
The option applies to manual metrics too, but does not affect other Micrometer registries.
All Micrometer metrics share `auto.metrics.micrometer` as their origin, so origin cannot
select just logging metrics.

Sentry checks ignored names before creating metric events. The registry also denies registration
when all possible exported names of a meter are ignored, avoiding recording and polling overhead.
Changing the list later still filters captured metrics, but does not remove existing meters or
reactivate previously returned no-op meters. Configure filters before registration for the lowest
overhead. Intentional ignores do not generate client reports.

Apply Micrometer `MeterFilter`s directly to the Sentry registry for Micrometer-only filtering:

```java
sentryRegistry.config().meterFilter(MeterFilter.denyNameStartsWith("jvm.buffer"));
```

Micrometer tags are application-provided metric data and are forwarded as supplied. Use a
registry-local filter or Sentry's metrics `beforeSend` callback to remove sensitive or
high-cardinality attributes.

## Shutdown

Remove the registry from its owning global or composite registry, close it, and then close Sentry:

```java
Metrics.removeRegistry(sentryRegistry);
sentryRegistry.close();
Sentry.close();
```

Closing the registry stops future polling without invoking passive callbacks or waiting for a
blocked callback to return. Metrics already accepted by Sentry remain available to the normal
Sentry flush and shutdown lifecycle.
