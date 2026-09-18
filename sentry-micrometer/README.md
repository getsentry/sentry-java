# sentry-micrometer

This module forwards Micrometer metrics to Sentry.

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
