# Sentry Spring Boot integration

This integration is ideal to Spring Boot users. It buids on top of [Sentry's core Java SDK](https://github.com/getsentry/sentry-java)
and also integrates well with logback or log4j2 if those are used in the application.

For more information check out [Sentry Spring Boot documentation](https://docs.sentry.io/platforms/java/guides/spring-boot/).

## Ignoring server transactions by HTTP status

Incoming server transactions that finish with HTTP status 301, 305-399, or 401-404 are not sent to
Sentry by default. Set `sentry.trace-ignore-status-codes` to replace the default with a
comma-separated list of individual status codes and inclusive ranges:

```properties
sentry.trace-ignore-status-codes=404,500-599
```

Use an empty value to trace every incoming HTTP status:

```properties
sentry.trace-ignore-status-codes=
```

For YAML configuration, use an empty list:

```yaml
sentry:
  trace-ignore-status-codes: []
```

The filter reads the root transaction's HTTP response status; child span statuses do not affect it.
Filtering happens after the response status is known, so the request may already have propagated a
sampled trace, and local child spans are discarded with the transaction.
