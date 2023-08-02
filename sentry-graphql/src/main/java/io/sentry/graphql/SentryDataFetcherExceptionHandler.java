package io.sentry.graphql;

import static io.sentry.TypeCheckHint.GRAPHQL_HANDLER_PARAMETERS;

import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import io.sentry.Hint;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Captures exceptions that occur during data fetching, passes them to Sentry and invokes a delegate
 * exception handler.
 *
 * @deprecated please use {@link SentryGenericDataFetcherExceptionHandler} in combination with
 *     {@link SentryInstrumentation} instead.
 */
@Deprecated
public final class SentryDataFetcherExceptionHandler implements DataFetcherExceptionHandler {
  private final @NotNull IHub hub;
  private final @NotNull DataFetcherExceptionHandler delegate;

  public SentryDataFetcherExceptionHandler(
      final @NotNull IHub hub, final @NotNull DataFetcherExceptionHandler delegate) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.delegate = Objects.requireNonNull(delegate, "delegate is required");
    SentryIntegrationPackageStorage.getInstance().addIntegration("GrahQLLegacyExceptionHandler");
  }

  public SentryDataFetcherExceptionHandler(final @NotNull DataFetcherExceptionHandler delegate) {
    this(HubAdapter.getInstance(), delegate);
  }

  @Override
  @SuppressWarnings("deprecation")
  public DataFetcherExceptionHandlerResult onException(
      final @NotNull DataFetcherExceptionHandlerParameters handlerParameters) {
    final Hint hint = new Hint();
    hint.set(GRAPHQL_HANDLER_PARAMETERS, handlerParameters);

    hub.captureException(handlerParameters.getException(), hint);
    return delegate.onException(handlerParameters);
  }
}
