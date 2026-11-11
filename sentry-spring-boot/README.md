# Sentry Spring Boot integration

This integration is ideal to Spring Boot users. It buids on top of [Sentry's core Java SDK](https://github.com/getsentry/sentry-java)
and also integrates well with logback or log4j2 if those are used in the application.

For more information check out [Sentry Spring Boot documentation](https://docs.sentry.io/platforms/java/guides/spring-boot/).

## Ignoring server transactions by HTTP status

Incoming server transactions that finish with HTTP status 404 are not sent to Sentry by default.
Set `sentry.ignored-server-http-status-codes-for-tracing` to replace the default with a
comma-separated list of individual status codes and inclusive ranges:

```properties
sentry.ignored-server-http-status-codes-for-tracing=404,500-599
```

Use an empty value to trace every incoming HTTP status:

```properties
sentry.ignored-server-http-status-codes-for-tracing=
```

For YAML configuration, use an empty list:

```yaml
sentry:
  ignored-server-http-status-codes-for-tracing: []
```

The filter reads the root transaction's HTTP response status; child span statuses do not affect it.
Filtering happens after the response status is known, so the request may already have propagated a
sampled trace, and local child spans are discarded with the transaction.
