package io.sentry.spring.graphql;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.sentry.IHub;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.graphql.ExceptionReporter;
import io.sentry.graphql.SentrySubscriptionHandler;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

public final class SentryDgsSubscriptionHandler implements SentrySubscriptionHandler {

  public SentryDgsSubscriptionHandler() {
    SentryIntegrationPackageStorage.getInstance().addIntegration("NetflixDGSGrahQL");
  }

  @Override
  public Object onSubscriptionResult(
      final @NotNull Object result,
      final @NotNull IHub hub,
      final @NotNull ExceptionReporter exceptionReporter,
      final @NotNull InstrumentationFieldFetchParameters parameters) {
    if (result instanceof Flux) {
      Flux<?> flux = (Flux<?>) result;
      return flux.doOnError(
          throwable -> {
            ExceptionReporter.ExceptionDetails exceptionDetails =
                new ExceptionReporter.ExceptionDetails(hub, parameters.getEnvironment(), true);
            exceptionReporter.captureThrowable(throwable, exceptionDetails, null);
          });
    }
    return result;
  }
}
