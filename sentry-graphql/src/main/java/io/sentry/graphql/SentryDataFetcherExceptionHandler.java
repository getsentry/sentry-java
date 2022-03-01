package io.sentry.graphql;

import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.util.Objects;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Captures exceptions that occur during data fetching, passes them to Sentry and invokes a delegate
 * exception handler.
 */
public final class SentryDataFetcherExceptionHandler implements DataFetcherExceptionHandler {
  private final @NotNull IHub hub;
  private final @NotNull DataFetcherExceptionHandler delegate;

  public SentryDataFetcherExceptionHandler(
      final @NotNull IHub hub, final @NotNull DataFetcherExceptionHandler delegate) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.delegate = Objects.requireNonNull(delegate, "delegate is required");
  }

  public SentryDataFetcherExceptionHandler(final @NotNull DataFetcherExceptionHandler delegate) {
    this(HubAdapter.getInstance(), delegate);
  }

  @Override
  @SuppressWarnings("deprecation")
  public DataFetcherExceptionHandlerResult onException(
      final @NotNull DataFetcherExceptionHandlerParameters handlerParameters) {
    final Map<String, Object> hintMap = new HashMap<>();
    hintMap.put("DataFetcherExceptionHandlerParameters", handlerParameters);

    hub.captureException(handlerParameters.getException(), hintMap);
    return delegate.onException(handlerParameters);
  }
}
