# sentry-reactor

This module provides a set of utilities to manually instrument your application to send data to Sentry when using [Reactor](https://projectreactor.io/).

## Setup

Please refer to the documentation on how to set up our [Java SDK](https://docs.sentry.io/platforms/java/),
or our [Spring](https://docs.sentry.io/platforms/java/guides/spring/) 
or [Spring Boot](https://docs.sentry.io/platforms/java/guides/spring-boot/) integrations if you are using Spring WebFlux.

If you're using our Spring Boot integration, this module will be available and set up automatically.

Otherwise, you'll need to perform the following steps to get started.

Add the latest version of `io.sentry.reactor` as a dependency.
Make sure you are using `io.micrometer:context-propagation:1.0.2` or later, provided by `io.projectreactor:reactor-core:3.5.3` or later.

Then, enable automatic context propagation, which should happen as early as possible in your application lifecycle:
```java
import reactor.core.publisher.Hooks;
// ...
Hooks.enableAutomaticContextPropagation();
```

Finally, enable the `SentryReactorThreadLocalAccessor`:
```java
import io.micrometer.context.ContextRegistry;
import io.sentry.reactor.SentryReactorThreadLocalAccessor;
// ...
ContextRegistry.getInstance().registerThreadLocalAccessor(SentryReactorThreadLocalAccessor());
```

You can also use SPI to enable it by creating a file in `resources/META-INF.services/io.micrometer.context.ThreadLocalAccessor` with the content:
```
io.sentry.reactor.SentryReactorThreadLocalAccessor
```
and then calling
```java
import io.micrometer.context.ContextRegistry;
// ...
ContextRegistry.getInstance().loadThreadLocalAccessors();
```

## Usage

You can use the provided utilities to wrap `Mono` and `Flux` objects to enable correct errors, breadcrumbs and tracing reporting in your application.

For normal use cases, you should wrap your operations on `Mono` or `Flux` objects using the `withSentry` function.
This will fork the *current scopes* and use them throughout the stream's execution context.

You can use the provided utilities to wrap `Mono` and `Flux` objects to enable correct error, breadcrumbs and tracing reporting for your reactive streams.

For more complex use cases, you can also use the `withSentryForkedRoots` to fork the root scopes or `withSentryScopes` to wrap the operation in arbitrary scopes.
For more information, you can consult our [scopes documentation](https://docs.sentry.io/platforms/java/enriching-events/scopes).

Examples of usage of this module with Spring WebFlux are provided in 
[sentry-samples-spring-boot-webflux](https://github.com/getsentry/sentry-java/tree/main/sentry-samples/sentry-samples-spring-boot-webflux)
and
[sentry-samples-spring-boot-webflux-jakarta](https://github.com/getsentry/sentry-java/tree/main/sentry-samples/sentry-samples-spring-boot-webflux-jakarta)
.
