# sentry-apollo-5

This module provides HTTP tracing and failed GraphQL request reporting for [Apollo Kotlin 5](https://www.apollographql.com/docs/kotlin/).

Please consult the documentation on how to install and use this integration in the Sentry Docs for [Android](https://docs.sentry.io/platforms/android/integrations/apollo5/) or [Java](https://docs.sentry.io/platforms/java/tracing/instrumentation/apollo5/).

## Usage

Add `io.sentry:sentry-apollo-5` and install the integration while building the Apollo client:

```kotlin
val apolloClient =
  ApolloClient.Builder()
    .serverUrl("https://example.com/graphql")
    .sentryTracing()
    .build()
```

The builder extension installs the Apollo interceptor before the cache and the Sentry HTTP interceptor.

Apollo rejects builder HTTP interceptors when a custom `NetworkTransport` is configured, so do not use `sentryTracing()` in that case. Add `SentryApollo5Interceptor` to the client builder and `SentryApollo5HttpInterceptor` to the custom transport manually.

Operation metadata is propagated between these interceptors through Apollo's execution context. Apollo's default HTTP request composer copies this context automatically. A custom `HttpRequestComposer` must copy it explicitly when building the HTTP request:

```kotlin
HttpRequest.Builder(method, url)
  .addExecutionContext(apolloRequest.executionContext)
  .build()
```

An entirely custom `NetworkTransport` must expose the metadata to `SentryApollo5HttpInterceptor` in the same way.

## Known limitations

- Failed GraphQL request detection matches the raw JSON response body for an `errors` field.
- Multipart and incremental responses retain the Apollo 4 failed-request inspection limitation.
- WebSocket subscriptions are not instrumented.
- Batching behavior depends on HTTP interceptor ordering.
- Normalized-cache hits do not create HTTP spans.
