package io.sentry.graphql;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.sentry.IScopes;
import org.jetbrains.annotations.NotNull;

public interface SentrySubscriptionHandler {
  @NotNull
  Object onSubscriptionResult(
      @NotNull Object result,
      @NotNull IScopes scopes,
      @NotNull ExceptionReporter exceptionReporter,
      @NotNull InstrumentationFieldFetchParameters parameters);
}
