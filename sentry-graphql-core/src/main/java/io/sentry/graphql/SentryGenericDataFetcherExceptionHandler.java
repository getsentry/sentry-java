package io.sentry.graphql;

import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import io.sentry.IScopes;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Captures exceptions that occur during data fetching, passes them to Sentry and invokes a delegate
 * exception handler.
 */
public final class SentryGenericDataFetcherExceptionHandler implements DataFetcherExceptionHandler {
  private final @NotNull SentryGraphqlExceptionHandler handler;

  public SentryGenericDataFetcherExceptionHandler(
      final @Nullable IScopes scopes, final @NotNull DataFetcherExceptionHandler delegate) {
    this.handler = new SentryGraphqlExceptionHandler(delegate);
  }

  public SentryGenericDataFetcherExceptionHandler(
      final @NotNull DataFetcherExceptionHandler delegate) {
    this(null, delegate);
  }

  @SuppressWarnings("deprecation")
  public @Nullable DataFetcherExceptionHandlerResult onException(
      final @NotNull DataFetcherExceptionHandlerParameters handlerParameters) {
    CompletableFuture<DataFetcherExceptionHandlerResult> futureResult =
        handleException(handlerParameters);

    if (futureResult != null) {
      try {
        return futureResult.get();
      } catch (InterruptedException | ExecutionException e) {
        return null;
      }
    } else {
      return null;
    }
  }

  @Override
  public @Nullable CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
      DataFetcherExceptionHandlerParameters handlerParameters) {
    return handler.handleException(
        handlerParameters.getException(),
        handlerParameters.getDataFetchingEnvironment(),
        handlerParameters);
  }
}
