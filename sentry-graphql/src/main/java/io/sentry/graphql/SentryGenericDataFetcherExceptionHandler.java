package io.sentry.graphql;

import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import io.sentry.IHub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Captures exceptions that occur during data fetching, passes them to Sentry and invokes a delegate
 * exception handler.
 */
public final class SentryGenericDataFetcherExceptionHandler implements DataFetcherExceptionHandler {
  private final @NotNull SentryGraphqlExceptionHandler handler;

  public SentryGenericDataFetcherExceptionHandler(
      final @Nullable IHub hub, final @NotNull DataFetcherExceptionHandler delegate) {
    this.handler = new SentryGraphqlExceptionHandler(delegate);
  }

  public SentryGenericDataFetcherExceptionHandler(
      final @NotNull DataFetcherExceptionHandler delegate) {
    this(null, delegate);
  }

  @Override
  @SuppressWarnings("deprecation")
  public @Nullable DataFetcherExceptionHandlerResult onException(
      final @NotNull DataFetcherExceptionHandlerParameters handlerParameters) {
    return handler.onException(
        handlerParameters.getException(),
        handlerParameters.getDataFetchingEnvironment(),
        handlerParameters);
  }
}
