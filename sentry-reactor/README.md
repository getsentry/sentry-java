# sentry-reactor

This module provides a set of utilities to use Sentry with [Reactor](https://projectreactor.io/).

## Setup

Please refer to the documentation on how to set up our [Java SDK](https://docs.sentry.io/platforms/java/),
or our [Spring](https://docs.sentry.io/platforms/java/guides/spring/) 
or [Spring Boot](https://docs.sentry.io/platforms/java/guides/spring-boot/) integrations if you're using Spring WebFlux.

If you're using our Spring Boot integration with Spring Boot 3 (`sentry-spring-boot-jakarta`), this module will be available and used under the hood to automatically instrument WebFlux.
If you're using our Spring integration with Spring 6 (`sentry-spring-jakarta`), you need to configure WebFlux as we do in [SentryWebFluxAutoConfiguration](https://github.com/getsentry/sentry-java/blob/a5098280b52aec28c71c150e286b5c937767634d/sentry-spring-boot-jakarta/src/main/java/io/sentry/spring/boot/jakarta/SentryWebfluxAutoConfiguration.java) for Spring Boot.
Then, read on to the next section to find out how to use the utilities.

Otherwise, you'll need to perform the following steps to get started.

Add the latest version of `io.sentry.reactor` as a dependency. 
Make sure you're using `io.micrometer:context-propagation:1.0.2` or later, and `io.projectreactor:reactor-core:3.5.3` or later.

Then, enable automatic context propagation:
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

You can use the utilities provided by this module to wrap `Mono` and `Flux` objects to enable correct errors, breadcrumbs and tracing in your application.

For normal use cases, you should wrap your operations on `Mono` or `Flux` objects using the `withSentry` function.
This will fork the *current scopes* and use them throughout the stream's execution context.

For more complex use cases, you can also use `withSentryForkedRoots` to fork the root scopes or `withSentryScopes` to wrap the operation in arbitrary scopes.

For more information on scopes and scope forking, please consult our [scopes documentation](https://docs.sentry.io/platforms/java/enriching-events/scopes).

Examples of usage of this module (with Spring WebFlux) are provided in 
[sentry-samples-spring-boot-webflux](https://github.com/getsentry/sentry-java/tree/main/sentry-samples/sentry-samples-spring-boot-webflux)
and
[sentry-samples-spring-boot-webflux-jakarta](https://github.com/getsentry/sentry-java/tree/main/sentry-samples/sentry-samples-spring-boot-webflux-jakarta)
.
