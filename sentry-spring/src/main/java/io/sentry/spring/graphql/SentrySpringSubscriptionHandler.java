package io.sentry.spring.graphql;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.sentry.IScopes;
import io.sentry.graphql.ExceptionReporter;
import io.sentry.graphql.SentrySubscriptionHandler;
import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.execution.SubscriptionPublisherException;
import reactor.core.publisher.Flux;

public final class SentrySpringSubscriptionHandler implements SentrySubscriptionHandler {

  @Override
  public @NotNull Object onSubscriptionResult(
      final @NotNull Object result,
      final @NotNull IScopes scopes,
      final @NotNull ExceptionReporter exceptionReporter,
      final @NotNull InstrumentationFieldFetchParameters parameters) {
    if (result instanceof Flux) {
      final @NotNull Flux<?> flux = (Flux<?>) result;
      return flux.doOnError(
          throwable -> {
            final @NotNull ExceptionReporter.ExceptionDetails exceptionDetails =
                new ExceptionReporter.ExceptionDetails(scopes, parameters.getEnvironment(), true);
            if (throwable instanceof SubscriptionPublisherException
                && throwable.getCause() != null) {
              exceptionReporter.captureThrowable(throwable.getCause(), exceptionDetails, null);
            } else {
              exceptionReporter.captureThrowable(throwable, exceptionDetails, null);
            }
          });
    }
    return result;
  }
}
