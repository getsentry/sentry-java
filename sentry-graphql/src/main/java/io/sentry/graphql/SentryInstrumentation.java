package io.sentry.graphql;

import graphql.ErrorClassification;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.NoOpHub;
import io.sentry.Sentry;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SpanStatus;
import io.sentry.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryInstrumentation extends SimpleInstrumentation {

  private static final @NotNull List<String> ERROR_TYPES_HANDLED_BY_DATA_FETCHERS =
      Arrays.asList("INTERNAL", "INTERNAL_ERROR");
  private final @Nullable BeforeSpanCallback beforeSpan;
  private final @NotNull SentrySubscriptionHandler subscriptionHandler;

  private final @NotNull ExceptionReporter exceptionReporter;

  // TODO ctor that takes a hub
  public SentryInstrumentation(final @Nullable BeforeSpanCallback beforeSpan) {
    this(beforeSpan, NoOpSubscriptionHandler.getInstance(), false);
  }

  public SentryInstrumentation(
      final @Nullable BeforeSpanCallback beforeSpan,
      final @NotNull SentrySubscriptionHandler subscriptionHandler,
      final boolean isSpring) {
    this.beforeSpan = beforeSpan;
    this.subscriptionHandler = subscriptionHandler;
    this.exceptionReporter = new ExceptionReporter(isSpring);
    SentryIntegrationPackageStorage.getInstance().addIntegration("GraphQL");
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-graphql", BuildConfig.VERSION_NAME);
  }

  public SentryInstrumentation(final @Nullable IHub hub) {
    this((BeforeSpanCallback) null);
  }

  public SentryInstrumentation(final @NotNull SentrySubscriptionHandler subscriptionHandler) {
    this(null, subscriptionHandler, false);
  }

  @Override
  public @NotNull InstrumentationState createState() {
    return new TracingState();
  }

  @Override
  public @NotNull InstrumentationContext<ExecutionResult> beginExecution(
      final @NotNull InstrumentationExecutionParameters parameters) {
    final TracingState tracingState = parameters.getInstrumentationState();
    final @NotNull IHub currentHub = Sentry.getCurrentHub();
    tracingState.setTransaction(currentHub.getSpan());
    parameters.getGraphQLContext().put("sentry.hub", currentHub);
    return super.beginExecution(parameters);
  }

  @Override
  public CompletableFuture<ExecutionResult> instrumentExecutionResult(
      ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
    return super.instrumentExecutionResult(executionResult, parameters)
        .whenComplete(
            (result, exception) -> {
              if (result != null) {
                final @Nullable GraphQLContext graphQLContext = parameters.getGraphQLContext();
                if (graphQLContext != null) {
                  final @NotNull List<Throwable> exceptions =
                      graphQLContext.getOrDefault(
                          "sentry.exceptions", new CopyOnWriteArrayList<Throwable>());
                  for (Throwable throwable : exceptions) {
                    exceptionReporter.captureThrowable(
                        throwable,
                        new ExceptionReporter.ExceptionDetails(
                            hubFromContext(graphQLContext), parameters),
                        result);
                  }
                }
                final @NotNull List<GraphQLError> errors = result.getErrors();
                if (errors != null) {
                  for (GraphQLError error : errors) {
                    // not capturing INTERNAL_ERRORS as they should be reported via graphQlContext
                    // above
                    String errorType = getErrorType(error);
                    if (errorType == null
                        || !ERROR_TYPES_HANDLED_BY_DATA_FETCHERS.contains(errorType)) {
                      exceptionReporter.captureThrowable(
                          new RuntimeException(error.getMessage()),
                          new ExceptionReporter.ExceptionDetails(
                              hubFromContext(graphQLContext), parameters),
                          result);
                    }
                  }
                }
              }
              if (exception != null) {
                exceptionReporter.captureThrowable(
                    exception,
                    new ExceptionReporter.ExceptionDetails(
                        hubFromContext(parameters.getGraphQLContext()), parameters),
                    null);
              }
            });
  }

  private @Nullable String getErrorType(final @Nullable GraphQLError error) {
    if (error == null) {
      return null;
    }
    final @Nullable ErrorClassification errorType = error.getErrorType();
    if (errorType != null) {
      return errorType.toString();
    }
    final @Nullable Map<String, Object> extensions = error.getExtensions();
    if (extensions != null) {
      Object extensionErrorType = extensions.get("errorType");
      if (extensionErrorType != null) {
        return extensionErrorType.toString();
      }
    }
    return null;
  }

  @Override
  public @NotNull InstrumentationContext<ExecutionResult> beginExecuteOperation(
      final @NotNull InstrumentationExecuteOperationParameters parameters) {
    final @Nullable ExecutionContext executionContext = parameters.getExecutionContext();
    if (executionContext != null) {
      final @Nullable OperationDefinition operationDefinition =
          executionContext.getOperationDefinition();
      if (operationDefinition != null) {
        final @Nullable OperationDefinition.Operation operation =
            operationDefinition.getOperation();
        final @Nullable String operationType =
            operation == null ? null : operation.name().toLowerCase(Locale.ROOT);
        hubFromContext(parameters.getExecutionContext().getGraphQLContext())
            .addBreadcrumb(
                Breadcrumb.graphqlOperation(
                    operationDefinition.getName(),
                    operationType,
                    StringUtils.toString(executionContext.getExecutionId())));
      }
    }
    return super.beginExecuteOperation(parameters);
  }

  private @NotNull IHub hubFromContext(final @Nullable GraphQLContext context) {
    if (context == null) {
      return NoOpHub.getInstance();
    }
    return context.getOrDefault("sentry.hub", NoOpHub.getInstance());
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
      final @Nullable ExecutionStepInfo executionStepInfo = environment.getExecutionStepInfo();
      if (executionStepInfo != null) {
        hubFromContext(parameters.getExecutionContext().getGraphQLContext())
            .addBreadcrumb(
                Breadcrumb.graphqlDataFetcher(
                    StringUtils.toString(executionStepInfo.getPath()),
                    GraphqlStringUtils.fieldToString(executionStepInfo.getField()),
                    GraphqlStringUtils.typeToString(executionStepInfo.getType()),
                    GraphqlStringUtils.objectTypeToString(executionStepInfo.getObjectType())));
      }
      final TracingState tracingState = parameters.getInstrumentationState();
      final ISpan transaction = tracingState.getTransaction();
      if (transaction != null) {
        final ISpan span = createSpan(transaction, parameters);
        try {
          final @Nullable Object tmpResult = dataFetcher.get(environment);
          final Object result =
              tmpResult == null
                  ? null
                  : subscriptionHandler.onSubscriptionResult(
                      tmpResult,
                      hubFromContext(environment.getGraphQlContext()),
                      exceptionReporter,
                      parameters);
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
        final Object result = dataFetcher.get(environment);
        if (result != null) {
          return subscriptionHandler.onSubscriptionResult(
              result,
              hubFromContext(environment.getGraphQlContext()),
              exceptionReporter,
              parameters);
        }
        return null;
      }
    };
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(
      InstrumentationFieldParameters parameters) {
    return super.beginSubscribedFieldEvent(parameters);
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

    return transaction.startChild(
        "graphql",
        parent.getName() + "." + parameters.getExecutionStepInfo().getPath().getSegmentName());
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
