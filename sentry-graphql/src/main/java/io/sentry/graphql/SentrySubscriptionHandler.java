package io.sentry.graphql;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.sentry.IHub;
import org.jetbrains.annotations.NotNull;

public interface SentrySubscriptionHandler {
  @NotNull
  Object onSubscriptionResult(
      @NotNull Object result,
      @NotNull IHub hub,
      @NotNull ExceptionReporter exceptionReporter,
      @NotNull InstrumentationFieldFetchParameters parameters);
}
