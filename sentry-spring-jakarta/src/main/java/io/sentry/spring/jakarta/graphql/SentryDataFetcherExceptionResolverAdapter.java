package io.sentry.spring.jakarta.graphql;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.schema.DataFetchingEnvironment;
import io.sentry.graphql.SentryGraphqlExceptionHandler;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;

@ApiStatus.Internal
public final class SentryDataFetcherExceptionResolverAdapter
    extends DataFetcherExceptionResolverAdapter {
  private final @NotNull SentryGraphqlExceptionHandler handler;

  public SentryDataFetcherExceptionResolverAdapter() {
    this.handler = new SentryGraphqlExceptionHandler(null);
  }

  @Override
  public boolean isThreadLocalContextAware() {
    return true;
  }

  @Override
  protected @Nullable GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
    List<GraphQLError> errors = resolveToMultipleErrors(ex, env);
    if (errors != null && !errors.isEmpty()) {
      return errors.get(0);
    }
    return null;
  }

  @Override
  protected @Nullable List<GraphQLError> resolveToMultipleErrors(
      Throwable ex, DataFetchingEnvironment env) {
    @Nullable DataFetcherExceptionHandlerResult result = handler.onException(ex, env, null);
    if (result != null) {
      return result.getErrors();
    }
    return null;
  }
}
