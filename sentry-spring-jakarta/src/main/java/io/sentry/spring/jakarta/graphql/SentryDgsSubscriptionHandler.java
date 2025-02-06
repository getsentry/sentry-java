package io.sentry.spring.jakarta.graphql;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.sentry.IScopes;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.graphql.ExceptionReporter;
import io.sentry.graphql.SentrySubscriptionHandler;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

public final class SentryDgsSubscriptionHandler implements SentrySubscriptionHandler {

  public SentryDgsSubscriptionHandler() {
    SentryIntegrationPackageStorage.getInstance().addIntegration("Spring6NetflixDGSGrahQL");
  }

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
            exceptionReporter.captureThrowable(throwable, exceptionDetails, null);
          });
    }
    return result;
  }
}
