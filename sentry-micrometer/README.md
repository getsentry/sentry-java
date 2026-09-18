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

Each active timer or distribution-summary recording creates one Sentry metric before the existing
Sentry metrics batch processor batches it for transport. Apply Micrometer `MeterFilter`s directly
to the Sentry registry to control volume and cardinality without affecting other registries:

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
