package io.sentry.graphql;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryInstrumentation extends SimpleInstrumentation {
  private static final String TRACE_ORIGIN = "auto.graphql.graphql";
  private final @NotNull IHub hub;
  private final @Nullable BeforeSpanCallback beforeSpan;

  public SentryInstrumentation(
      final @NotNull IHub hub, final @Nullable BeforeSpanCallback beforeSpan) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.beforeSpan = beforeSpan;
    SentryIntegrationPackageStorage.getInstance().addIntegration("GraphQL");
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-graphql", BuildConfig.VERSION_NAME);
  }

  public SentryInstrumentation(final @Nullable BeforeSpanCallback beforeSpan) {
    this(HubAdapter.getInstance(), beforeSpan);
  }

  public SentryInstrumentation(final @NotNull IHub hub) {
    this(hub, null);
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
    final TracingState tracingState = parameters.getInstrumentationState();
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
        final ISpan span = createSpan(transaction, parameters);
        try {
          final Object result = dataFetcher.get(environment);
          if (result instanceof CompletableFuture) {
            ((CompletableFuture<?>) result)
                .whenComplete(
                    (r, ex) -> {
                      if (ex != null) {
                        span.setThrowable(ex);
                        span.setStatus(SpanStatus.INTERNAL_ERROR);
                      } else {
                        span.setStatus(SpanStatus.OK);
                      }
                      finish(span, environment, r);
                    });
          } else {
            span.setStatus(SpanStatus.OK);
            finish(span, environment, result);
          }
          return result;
        } catch (Throwable e) {
          span.setThrowable(e);
          span.setStatus(SpanStatus.INTERNAL_ERROR);
          finish(span, environment);
          throw e;
        }
      } else {
        return dataFetcher.get(environment);
      }
    };
  }

  private void finish(
      final @NotNull ISpan span,
      final @NotNull DataFetchingEnvironment environment,
      final @Nullable Object result) {
    if (beforeSpan != null) {
      final ISpan newSpan = beforeSpan.execute(span, environment, result);
      if (newSpan == null) {
        // span is dropped
        span.getSpanContext().setSampled(false);
      } else {
        newSpan.finish();
      }
    } else {
      span.finish();
    }
  }

  private void finish(
      final @NotNull ISpan span, final @NotNull DataFetchingEnvironment environment) {
    finish(span, environment, null);
  }

  private @NotNull ISpan createSpan(
      @NotNull ISpan transaction, @NotNull InstrumentationFieldFetchParameters parameters) {
    final GraphQLOutputType type = parameters.getExecutionStepInfo().getParent().getType();
    GraphQLObjectType parent;
    if (type instanceof GraphQLNonNull) {
      parent = (GraphQLObjectType) ((GraphQLNonNull) type).getWrappedType();
    } else {
      parent = (GraphQLObjectType) type;
    }

    final @NotNull ISpan span =
        transaction.startChild(
            "graphql",
            parent.getName() + "." + parameters.getExecutionStepInfo().getPath().getSegmentName());

    span.getSpanContext().setOrigin(TRACE_ORIGIN);

    return span;
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

  @FunctionalInterface
  public interface BeforeSpanCallback {
    @Nullable
    ISpan execute(
        @NotNull ISpan span, @NotNull DataFetchingEnvironment environment, @Nullable Object result);
  }
}
