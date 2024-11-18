package io.sentry.graphql;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.sentry.IScopes;
import org.jetbrains.annotations.NotNull;

public final class NoOpSubscriptionHandler implements SentrySubscriptionHandler {

  private static final @NotNull NoOpSubscriptionHandler instance = new NoOpSubscriptionHandler();

  private NoOpSubscriptionHandler() {}

  public static @NotNull NoOpSubscriptionHandler getInstance() {
    return instance;
  }

  @Override
  public @NotNull Object onSubscriptionResult(
      @NotNull Object result,
      @NotNull IScopes scopes,
      @NotNull ExceptionReporter exceptionReporter,
      @NotNull InstrumentationFieldFetchParameters parameters) {
    return result;
  }
}
