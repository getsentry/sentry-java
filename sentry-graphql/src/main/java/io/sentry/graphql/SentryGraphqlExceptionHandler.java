package io.sentry.graphql;

import static io.sentry.graphql.SentryInstrumentation.SENTRY_EXCEPTIONS_CONTEXT_KEY;

import graphql.GraphQLContext;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryGraphqlExceptionHandler {
  private final @Nullable DataFetcherExceptionHandler delegate;
  private final @NotNull Object exceptionContextLock = new Object();

  public SentryGraphqlExceptionHandler(final @Nullable DataFetcherExceptionHandler delegate) {
    this.delegate = delegate;
  }

  public @Nullable CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
      final @NotNull Throwable throwable,
      final @Nullable DataFetchingEnvironment environment,
      final @Nullable DataFetcherExceptionHandlerParameters handlerParameters) {
    if (environment != null) {
      final @Nullable GraphQLContext graphQlContext = environment.getGraphQlContext();
      if (graphQlContext != null) {
        synchronized (exceptionContextLock) {
          final @NotNull List<Throwable> exceptions =
              graphQlContext.getOrDefault(
                  SENTRY_EXCEPTIONS_CONTEXT_KEY, new CopyOnWriteArrayList<Throwable>());
          exceptions.add(throwable);
          graphQlContext.put(SENTRY_EXCEPTIONS_CONTEXT_KEY, exceptions);
        }
      }
    }
    if (delegate != null) {
      return delegate.handleException(handlerParameters);
    } else {
      return null;
    }
  }
}
