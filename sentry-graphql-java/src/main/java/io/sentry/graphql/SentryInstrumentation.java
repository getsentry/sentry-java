package io.sentry.graphql;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryInstrumentation extends SimpleInstrumentation {
  private final @NotNull IHub hub;

  public SentryInstrumentation(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  public SentryInstrumentation() {
    this(HubAdapter.getInstance());
  }

  @Override
  public @NotNull InstrumentationState createState() {
    return new TracingState();
  }

  @Override
  public @NotNull InstrumentationContext<ExecutionResult> beginExecution(
      final @NotNull InstrumentationExecutionParameters parameters) {
    TracingState tracingState = parameters.getInstrumentationState();
    tracingState.setTransaction(hub.getSpan());
    return super.beginExecution(parameters);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public @NotNull DataFetcher<?> instrumentDataFetcher(
      final @NotNull DataFetcher<?> dataFetcher,
      final @NotNull InstrumentationFieldFetchParameters parameters) {
    // We only care about user code
    if (parameters.isTrivialDataFetcher()) {
      return dataFetcher;
    }

    return environment -> {
      final TracingState tracingState = parameters.getInstrumentationState();
      final ISpan transaction = tracingState.getTransaction();
      if (transaction != null) {
        final ISpan span = transaction.startChild(findDataFetcherTag(parameters));
        final Object result = dataFetcher.get(environment);
        if (result instanceof CompletableFuture) {
          ((CompletableFuture<?>) result)
              .whenComplete(
                  (r, ex) -> {
                    span.finish();
                  });
        } else {
          span.finish();
        }
        return result;
      } else {
        return dataFetcher.get(environment);
      }
    };
  }

  @Override
  public @NotNull CompletableFuture<ExecutionResult> instrumentExecutionResult(
      final @NotNull ExecutionResult executionResult,
      final @NotNull InstrumentationExecutionParameters parameters) {
    TracingState tracingState = parameters.getInstrumentationState();
    final ISpan transaction = tracingState.getTransaction();

    if (transaction != null) {
      transaction.finish();
    }

    return super.instrumentExecutionResult(executionResult, parameters);
  }

  private @NotNull String findDataFetcherTag(
      final @NotNull InstrumentationFieldFetchParameters parameters) {
    final GraphQLOutputType type = parameters.getExecutionStepInfo().getParent().getType();
    GraphQLObjectType parent;
    if (type instanceof GraphQLNonNull) {
      parent = (GraphQLObjectType) ((GraphQLNonNull) type).getWrappedType();
    } else {
      parent = (GraphQLObjectType) type;
    }

    return parent.getName() + "." + parameters.getExecutionStepInfo().getPath().getSegmentName();
  }

  static final class TracingState implements InstrumentationState {
    private @Nullable ISpan transaction;

    public @Nullable ISpan getTransaction() {
      return transaction;
    }

    public void setTransaction(final @Nullable ISpan transaction) {
      this.transaction = transaction;
    }
  }
}
