# sentry-opentelemetry-agent

## How to use it

Download the latest `sentry-opentelemetry-agent.jar` and use it when launching your Java
application by adding the following parameters to your `java` command:

`$ SENTRY_PROPERTIES_FILE=sentry.properties java -javaagent:sentry-opentelemetry-agent.jar -jar your-application.jar`

Your `sentry.properties` could look like this:

```properties
# NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your Sentry project/dashboard
dsn=https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563
traces-sample-rate=1.0
```

For more details on configuring Sentry via `sentry.properties` please see the
[docs page](https://docs.sentry.io/platforms/java/configuration/).

As an alternative to the `SENTRY_PROPERTIES_FILE` environment variable you can provide individual
settings as environment variables (e.g. `SENTRY_DSN=...`).

## Controlling auto initialization of Sentry

By default, if you pass either `SENTRY_DSN` or `SENTRY_PROPERTIES_FILE` as environment variable,
Sentry will automatically be initialized by this agent. To disable this behaviour, you can set
`SENTRY_AUTO_INIT=false` as environment variable. You will then have to use a Sentry SDK that takes care of initialization or initialize Sentry manually inside
the target application:

```
Sentry.init(
        options -> {
          options.setDsn("...");
          ...
        }
)
```

## Debugging

To enable debug logging for Sentry, please provide `SENTRY_DEBUG=true` as environment variable or
add `debug=true` to your `sentry.properties`.

To also show debug output for OpenTelemetry please add `-Dotel.javaagent.debug=true` to the command.

## Getting rid of exporter error messages

In case you are using this agent without needing to use any OpenTelemetry exporters you can add
the following environment variables to turn off exporters and stop seeing error messages about 
servers not being reachable in the logs.

Example log message:
```
ERROR io.opentelemetry.exporter.internal.grpc.OkHttpGrpcExporter - Failed to export spans. The request could not be executed. Full error message: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4317
ERROR io.opentelemetry.exporter.internal.grpc.OkHttpGrpcExporter - Failed to export metrics. The request could not be executed. Full error message: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4317
ERROR io.opentelemetry.exporter.internal.http.HttpExporter - Failed to export logs. The request could not be executed. Full error message: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4318
```

### Traces

To turn off exporting of traces you can set `OTEL_TRACES_EXPORTER=none`
see [OpenTelemetry GitHub](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#otlp-exporter-span-metric-and-log-exporters)

### Metrics

To turn off exporting of metrics you can set `OTEL_METRICS_EXPORTER=none`
see [OpenTelemetry GitHub](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#otlp-exporter-span-metric-and-log-exporters)

### Logs

To turn off log exporting, set `OTEL_LOGS_EXPORTER=none` 
see [OpenTelemetry GitHub](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#otlp-exporter-span-metric-and-log-exporters).
