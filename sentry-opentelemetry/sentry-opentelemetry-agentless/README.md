# sentry-opentelemetry-agentless

## How to use it

Add the latest `sentry-opentelemetry-agentless` module as a dependency and add a `sentry.properties` 
configuration file to your project that could look like this:

```properties
# NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your Sentry project/dashboard
dsn=https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563
traces-sample-rate=1.0
```

For more details on configuring Sentry via `sentry.properties` please see the
[docs page](https://docs.sentry.io/platforms/java/configuration/).

As an alternative to the `SENTRY_PROPERTIES_FILE` environment variable you can provide individual
settings as environment variables (e.g. `SENTRY_DSN=...`).

Run your application with the following JVM arguments:
```
-Dotel.java.global-autoconfigure.enabled=true
```

You may also want to set the following environment variables to if you do not use OpenTelemetry exporters:
`OTEL_LOGS_EXPORTER=none;OTEL_METRICS_EXPORTER=none;OTEL_TRACES_EXPORTER=none`

Alternatively you can initialize OpenTelemetry programmatically like this:

```java
// Initialize OpenTelemetry by using the AutoConfiguredOpenTelemetrySdk which automatically
// registers the `SentrySpanProcessor` and `SentryPropagator` and others.
// Also, you need to disable the OTEL exporters if you do not use them.
AutoConfiguredOpenTelemetrySdk.builder()
  .setResultAsGlobal()
  .addPropertiesSupplier(() -> {
final Map<String, String> properties = new HashMap<>();
    properties.put("otel.logs.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.traces.exporter", "none");
    return properties;
  })
  .build();
```

If you're not using `sentry.properties` or environment variables you can then initialize Sentry programmatically as usual:

```java
// Initialize Sentry
Sentry.init(
    options -> {
    options.setDsn("...");
      ...
  }
)
```
