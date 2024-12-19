# sentry-opentelemetry-agentless-spring

*NOTE: Our OpenTelemetry modules are still experimental. Any feedback is welcome.*

## How to use it

Add the latest `sentry-opentelemetry-agentless-spring` module as a dependency and add a `sentry.properties` 
configuration file to your project that could look like this:

```properties
# NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your Sentry project/dashboard
dsn=https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563
traces-sample-rate=1.0
```

For more details on configuring Sentry via `sentry.properties` please see the
[docs page](https://docs.sentry.io/platforms/java/configuration/).

As an alternative to the `SENTRY_PROPERTIES_FILE` environment variable you can provide individual
settings as environment variables (e.g. `SENTRY_DSN=...`) or you may initialize `Sentry` inside
your target application. If you do so, please make sure to apply OpenTelemetry specific options, e.g.
like this:

```
Sentry.init(
        options -> {
          options.setDsn("...");
          ...
          OpenTelemetryUtil.applyOpenTelemetryOptions(options, false);
        }
)
```

## Getting rid of exporter error messages

In case you are using this module without needing to use any OpenTelemetry exporters you can add
the following environment variables to turn off exporters and stop seeing error messages about 
servers not being reachable in the logs.

Example log message:
```
ERROR io.opentelemetry.exporter.internal.grpc.OkHttpGrpcExporter - Failed to export spans. The request could not be executed. Full error message: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4317
ERROR io.opentelemetry.exporter.internal.grpc.OkHttpGrpcExporter - Failed to export metrics. The request could not be executed. Full error message: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4317
```

### Traces

To turn off exporting of traces you can set `OTEL_TRACES_EXPORTER=none`
see [OpenTelemetry GitHub](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#otlp-exporter-span-metric-and-log-exporters)

### Metrics

To turn off exporting of metrics you can set `OTEL_METRICS_EXPORTER=none`
see [OpenTelemetry GitHub](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#otlp-exporter-span-metric-and-log-exporters)
