package io.sentry.graphql;

import graphql.ErrorClassification;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.NoOpScopes;
import io.sentry.Sentry;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.TypeCheckHint;
import io.sentry.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SentryGraphqlInstrumentation {

  public static final @NotNull String SENTRY_SCOPES_CONTEXT_KEY = "sentry.scopes";
  public static final @NotNull String SENTRY_EXCEPTIONS_CONTEXT_KEY = "sentry.exceptions";

  private static final @NotNull List<String> ERROR_TYPES_HANDLED_BY_DATA_FETCHERS =
      Arrays.asList(
          "INTERNAL_ERROR", // spring-graphql
          "INTERNAL", // Netflix DGS
          "DataFetchingException" // raw graphql-java
          );
  private final @Nullable BeforeSpanCallback beforeSpan;
  private final @NotNull SentrySubscriptionHandler subscriptionHandler;
  private final @NotNull ExceptionReporter exceptionReporter;
  private final @NotNull List<String> ignoredErrorTypes;
  private final @NotNull String traceOrigin;

  /**
   * @param beforeSpan callback when a span is created
   * @param subscriptionHandler can report subscription errors
   * @param captureRequestBodyForNonSubscriptions false if request bodies should not be captured by
   *     this integration for query and mutation operations. This can be used to prevent unnecessary
   *     work by not adding the request body when another integration will add it anyways, as is the
   *     case with our spring integration for WebMVC.
   * @param ignoredErrorTypes list of error types that should not be captured and sent to Sentry
   */
  public SentryGraphqlInstrumentation(
      final @Nullable BeforeSpanCallback beforeSpan,
      final @NotNull SentrySubscriptionHandler subscriptionHandler,
      final boolean captureRequestBodyForNonSubscriptions,
      final @NotNull List<String> ignoredErrorTypes,
      final @NotNull String traceOrigin) {
    this(
        beforeSpan,
        subscriptionHandler,
        new ExceptionReporter(captureRequestBodyForNonSubscriptions),
        ignoredErrorTypes,
        traceOrigin);
  }

  @TestOnly
  public SentryGraphqlInstrumentation(
      final @Nullable BeforeSpanCallback beforeSpan,
      final @NotNull SentrySubscriptionHandler subscriptionHandler,
      final @NotNull ExceptionReporter exceptionReporter,
      final @NotNull List<String> ignoredErrorTypes,
      final @NotNull String traceOrigin) {
    this.beforeSpan = beforeSpan;
    this.subscriptionHandler = subscriptionHandler;
    this.exceptionReporter = exceptionReporter;
    this.ignoredErrorTypes = ignoredErrorTypes;
    this.traceOrigin = traceOrigin;
  }

  public @NotNull InstrumentationState createState() {
    return new TracingState();
  }

  public void beginExecution(
      final @NotNull InstrumentationExecutionParameters parameters,
      final @NotNull TracingState tracingState) {
    final @NotNull IScopes currentScopes = Sentry.getCurrentScopes();
    tracingState.setTransaction(currentScopes.getSpan());
    parameters.getGraphQLContext().put(SENTRY_SCOPES_CONTEXT_KEY, currentScopes);
  }

  public void instrumentExecutionResultComplete(
      final @NotNull InstrumentationExecutionParameters parameters,
      final @Nullable ExecutionResult result,
      final @Nullable Throwable exception) {
    if (result != null) {
      final @Nullable GraphQLContext graphQLContext = parameters.getGraphQLContext();
      if (graphQLContext != null) {
        final @NotNull List<Throwable> exceptions =
            graphQLContext.getOrDefault(
                SENTRY_EXCEPTIONS_CONTEXT_KEY, new CopyOnWriteArrayList<Throwable>());
        for (Throwable throwable : exceptions) {
          exceptionReporter.captureThrowable(
              throwable,
              new ExceptionReporter.ExceptionDetails(
                  scopesFromContext(graphQLContext), parameters, false),
              result);
        }
      }
      final @NotNull List<GraphQLError> errors = result.getErrors();
      if (errors != null) {
        for (GraphQLError error : errors) {
          String errorType = getErrorType(error);
          if (!isIgnored(errorType)) {
            exceptionReporter.captureThrowable(
                new RuntimeException(error.getMessage()),
                new ExceptionReporter.ExceptionDetails(
                    scopesFromContext(graphQLContext), parameters, false),
                result);
          }
        }
      }
    }
    if (exception != null) {
      exceptionReporter.captureThrowable(
          exception,
          new ExceptionReporter.ExceptionDetails(
              scopesFromContext(parameters.getGraphQLContext()), parameters, false),
          null);
    }
  }

  private boolean isIgnored(final @Nullable String errorType) {
    if (errorType == null) {
      return false;
    }

    // not capturing INTERNAL_ERRORS as they should be reported via graphQlContext above
    // also not capturing error types explicitly ignored by users
    return ERROR_TYPES_HANDLED_BY_DATA_FETCHERS.contains(errorType)
        || ignoredErrorTypes.contains(errorType);
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
      return StringUtils.toString(extensions.get("errorType"));
    }
    return null;
  }

  public void beginExecuteOperation(
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
        scopesFromContext(parameters.getExecutionContext().getGraphQLContext())
            .addBreadcrumb(
                Breadcrumb.graphqlOperation(
                    operationDefinition.getName(),
                    operationType,
                    StringUtils.toString(executionContext.getExecutionId())));
      }
    }
  }

  private @NotNull IScopes scopesFromContext(final @Nullable GraphQLContext context) {
    if (context == null) {
      return NoOpScopes.getInstance();
    }
    return context.getOrDefault(SENTRY_SCOPES_CONTEXT_KEY, NoOpScopes.getInstance());
  }

  @SuppressWarnings({"FutureReturnValueIgnored", "deprecation"})
  public @NotNull DataFetcher<?> instrumentDataFetcher(
      final @NotNull DataFetcher<?> dataFetcher,
      final @NotNull InstrumentationFieldFetchParameters parameters,
      final @NotNull TracingState tracingState) {
    // We only care about user code
    if (parameters.isTrivialDataFetcher()) {
      return dataFetcher;
    }

    return environment -> {
      final @Nullable ExecutionStepInfo executionStepInfo = environment.getExecutionStepInfo();
      if (executionStepInfo != null) {
        Hint hint = new Hint();
        hint.set(TypeCheckHint.GRAPHQL_DATA_FETCHING_ENVIRONMENT, environment);
        scopesFromContext(parameters.getExecutionContext().getGraphQLContext())
            .addBreadcrumb(
                Breadcrumb.graphqlDataFetcher(
                    StringUtils.toString(executionStepInfo.getPath()),
                    GraphqlStringUtils.fieldToString(executionStepInfo.getField()),
                    GraphqlStringUtils.typeToString(executionStepInfo.getType()),
                    GraphqlStringUtils.objectTypeToString(executionStepInfo.getObjectType())),
                hint);
      }
      final ISpan transaction = tracingState.getTransaction();
      if (transaction != null) {
        final ISpan span = createSpan(transaction, parameters);
        try {
          final @Nullable Object tmpResult = dataFetcher.get(environment);
          final @Nullable Object result =
              maybeCallSubscriptionHandler(parameters, environment, tmpResult);
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
        return maybeCallSubscriptionHandler(parameters, environment, result);
      }
    };
  }

  private @Nullable Object maybeCallSubscriptionHandler(
      final @NotNull InstrumentationFieldFetchParameters parameters,
      final @NotNull DataFetchingEnvironment environment,
      final @Nullable Object tmpResult) {
    if (tmpResult == null) {
      return null;
    }

    if (OperationDefinition.Operation.SUBSCRIPTION.equals(
        environment.getOperationDefinition().getOperation())) {
      return subscriptionHandler.onSubscriptionResult(
          tmpResult,
          scopesFromContext(environment.getGraphQlContext()),
          exceptionReporter,
          parameters);
    }

    return tmpResult;
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
    final @NotNull SpanOptions spanOptions = new SpanOptions();
    spanOptions.setOrigin(traceOrigin);
    final @NotNull ISpan span =
        transaction.startChild(
            "graphql",
            parent.getName() + "." + parameters.getExecutionStepInfo().getPath().getSegmentName(),
            spanOptions);

    return span;
  }

  public static final class TracingState implements InstrumentationState {
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
