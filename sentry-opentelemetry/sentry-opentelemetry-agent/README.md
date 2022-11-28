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
settings as environment variables (e.g. `SENTRY_DSN=...`) or you may initialize `Sentry` inside
your target application. If you do so, please make sure to set the `instrumenter` to `otel`, e.g.
like this:

```
Sentry.init(
        options -> {
          options.setDsn("...");
          ...
          options.setInstrumenter(Instrumenter.OTEL);
        }
)
```

Using the `otel` instrumenter will ensure `Sentry` instrumentation will be done via OpenTelemetry
and integrations as well as direct interactions with transactions and spans have no effect.

## Debugging

To enable debug logging for Sentry, please provide `SENTRY_DEBUG=true` as environment variable or
add `debug=true` to your `sentry.properties`.

To also show debug output for OpenTelemetry please add `-Dotel.javaagent.debug=true` to the command.
