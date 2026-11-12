# Sentry Spring Boot integration

This integration is ideal to Spring Boot users. It buids on top of [Sentry's core Java SDK](https://github.com/getsentry/sentry-java)
and also integrates well with logback or log4j2 if those are used in the application.

For more information check out [Sentry Spring Boot documentation](https://docs.sentry.io/platforms/java/guides/spring-boot/).

## Ignoring server transactions by HTTP status

Set `sentry.ignored-server-http-status-codes-for-tracing` to a comma-separated list of individual
status codes and inclusive ranges:

```properties
sentry.ignored-server-http-status-codes-for-tracing=404,500-599
```

Matching incoming server transactions are not sent to Sentry. The filter reads the root
transaction's HTTP response status; child span statuses do not affect it. The option is disabled
when it is unset or empty.
