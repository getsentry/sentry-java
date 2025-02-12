# sentry-reactor

This module provides a set of utilities to use Sentry with [Reactor](https://projectreactor.io/).

## Setup

Please refer to the documentation on how to set up our [Java SDK](https://docs.sentry.io/platforms/java/),
or our [Spring](https://docs.sentry.io/platforms/java/guides/spring/) 
or [Spring Boot](https://docs.sentry.io/platforms/java/guides/spring-boot/) integrations if you're using Spring WebFlux.

If you're using our Spring Boot SDK with Spring Boot (`sentry-spring-boot` or `sentry-spring-boot-jakarta`), this module will be available and used under the hood to automatically instrument WebFlux.
If you're using our Spring SDK (`sentry-spring` or `sentry-spring-jakarta`), you need to configure WebFlux as we do in [SentryWebFluxAutoConfiguration](https://github.com/getsentry/sentry-java/blob/a5098280b52aec28c71c150e286b5c937767634d/sentry-spring-boot-jakarta/src/main/java/io/sentry/spring/boot/jakarta/SentryWebfluxAutoConfiguration.java) for Spring Boot.

Otherwise, read on to find out how to set up and use the integration.

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

For example:
```java
import reactor.core.publisher.Mono;
import io.sentry.Sentry;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.TransactionOptions; 

TransactionOptions txOptions = new TransactionOptions();
txOptions.setBindToScope(true);
ITransaction tx = Sentry.startTransaction("Transaction", "op", txOptions);
ISpan child = tx.startChild("Outside Mono", "op")
Sentry.captureMessage("Message outside Mono")
child.finish()
String result = SentryReactorUtils.withSentry(
  Mono.just("hello")
    .map({ (it) ->
      ISpan span = Sentry.getCurrentScopes().transaction.startChild("Inside Mono", "map");
      Sentry.captureMessage("Message inside Mono");
      span.finish();
      return it;
    })
).block();
System.out.println(result);
tx.finish();
```

For more complex use cases, you can also use `withSentryForkedRoots` to fork the root scopes or `withSentryScopes` to wrap the operation in arbitrary scopes.

For more information on scopes and scope forking, please consult our [scopes documentation](https://docs.sentry.io/platforms/java/enriching-events/scopes).

Examples of usage of this module (with Spring WebFlux) are provided in 
[sentry-samples-spring-boot-webflux](https://github.com/getsentry/sentry-java/tree/main/sentry-samples/sentry-samples-spring-boot-webflux)
and
[sentry-samples-spring-boot-webflux-jakarta](https://github.com/getsentry/sentry-java/tree/main/sentry-samples/sentry-samples-spring-boot-webflux-jakarta)
.
