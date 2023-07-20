package io.sentry.graphql;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.sentry.IHub;

public final class NoOpSubscriptionHandler implements SentrySubscriptionHandler {

  private static final NoOpSubscriptionHandler instance = new NoOpSubscriptionHandler();

  private NoOpSubscriptionHandler() {}

  public static NoOpSubscriptionHandler getInstance() {
    return instance;
  }

  @Override
  public Object onSubscriptionResult(
      Object result,
      IHub hub,
      ExceptionReporter exceptionReporter,
      InstrumentationFieldFetchParameters parameters) {
    return result;
  }
}
