---
alwaysApply: false
description: Metrics API
---
# Java SDK Metrics API

Metrics are enabled by default.

API has been namespaced under `Sentry.metrics()` and `IScopes.metrics()` using the `IMetricsApi` interface and `MetricsApi` implementation.

Options are namespaced under `SentryOptions.getMetrics()`.

Three different APIs exist:
- `count`: Counters are one of the more basic types of metrics and can be used to count certain event occurrences.
- `distribution`: Distributions help you get the most insights from your data by allowing you to obtain aggregations such as p90, min, max, and avg.
- `gauge`: Gauges let you obtain aggregates like min, max, avg, sum, and count. They can be represented in a more space-efficient way than distributions, but they can't be used to get percentiles. If percentiles aren't important to you, we recommend using gauges.

Refer to `SentryMetricsEvent` for details about available fields.

`MetricsBatchProcessor` handles batching (`MAX_BATCH_SIZE`), automatic sending of metrics after a timeout (`FLUSH_AFTER_MS`) and rejecting if `MAX_QUEUE_SIZE` has been hit.

The flow is `IMetricsApi` -> `IMetricsBatchProcessor` -> `SentryClient.captureBatchedMetricsEvents` -> `ITransport`.

Each `SentryMetricsEvent` goes through `SentryOptions.metrics.beforeSend` (if configured) and can be modified or dropped.

For sending, a batch of `SentryMetricsEvent` objects is sent inside a `SentryMetricsEvents` object.
