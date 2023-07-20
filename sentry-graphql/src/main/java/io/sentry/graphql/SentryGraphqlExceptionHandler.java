package io.sentry.graphql;

import graphql.GraphQLContext;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryGraphqlExceptionHandler {
  private final @Nullable DataFetcherExceptionHandler delegate;

  public SentryGraphqlExceptionHandler(final @Nullable DataFetcherExceptionHandler delegate) {
    this.delegate = delegate;
  }

  @SuppressWarnings("deprecation")
  public @Nullable DataFetcherExceptionHandlerResult onException(
      final @NotNull Throwable throwable,
      final @Nullable DataFetchingEnvironment environment,
      final @Nullable DataFetcherExceptionHandlerParameters handlerParameters) {
    if (environment != null) {
      final @Nullable GraphQLContext graphQlContext = environment.getGraphQlContext();
      if (graphQlContext != null) {
        final @NotNull List<Throwable> exceptions =
            graphQlContext.getOrDefault("sentry.exceptions", new CopyOnWriteArrayList<Throwable>());
        exceptions.add(throwable);
        graphQlContext.put("sentry.exceptions", exceptions);
      }
    }
    if (delegate != null) {
      return delegate.onException(handlerParameters);
    } else {
      return null;
    }
  }
}
