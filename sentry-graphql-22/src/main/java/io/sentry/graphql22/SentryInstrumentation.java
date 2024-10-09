package io.sentry.graphql22;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.graphql.ExceptionReporter;
import io.sentry.graphql.SentryGraphqlInstrumentation;
import io.sentry.graphql.SentrySubscriptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@SuppressWarnings("deprecation")
public final class SentryInstrumentation
    extends graphql.execution.instrumentation.SimpleInstrumentation {

  /**
   * @deprecated please use {@link SentryGraphqlInstrumentation#SENTRY_SCOPES_CONTEXT_KEY}
   */
  @Deprecated
  public static final @NotNull String SENTRY_SCOPES_CONTEXT_KEY =
      SentryGraphqlInstrumentation.SENTRY_SCOPES_CONTEXT_KEY;

  /**
   * @deprecated please use {@link SentryGraphqlInstrumentation#SENTRY_EXCEPTIONS_CONTEXT_KEY}
   */
  @Deprecated
  public static final @NotNull String SENTRY_EXCEPTIONS_CONTEXT_KEY =
      SentryGraphqlInstrumentation.SENTRY_EXCEPTIONS_CONTEXT_KEY;

  private static final String TRACE_ORIGIN = "auto.graphql.graphql22";
  private final @NotNull SentryGraphqlInstrumentation instrumentation;

  /**
   * @param beforeSpan callback when a span is created
   * @param subscriptionHandler can report subscription errors
   * @param captureRequestBodyForNonSubscriptions false if request bodies should not be captured by
   *     this integration for query and mutation operations. This can be used to prevent unnecessary
   *     work by not adding the request body when another integration will add it anyways, as is the
   *     case with our spring integration for WebMVC.
   */
  public SentryInstrumentation(
      final @Nullable SentryGraphqlInstrumentation.BeforeSpanCallback beforeSpan,
      final @NotNull SentrySubscriptionHandler subscriptionHandler,
      final boolean captureRequestBodyForNonSubscriptions) {
    this(
        beforeSpan,
        subscriptionHandler,
        new ExceptionReporter(captureRequestBodyForNonSubscriptions),
        new ArrayList<>());
  }

  /**
   * @param beforeSpan callback when a span is created
   * @param subscriptionHandler can report subscription errors
   * @param captureRequestBodyForNonSubscriptions false if request bodies should not be captured by
   *     this integration for query and mutation operations. This can be used to prevent unnecessary
   *     work by not adding the request body when another integration will add it anyways, as is the
   *     case with our spring integration for WebMVC.
   * @param ignoredErrorTypes list of error types that should not be captured and sent to Sentry
   */
  public SentryInstrumentation(
      final @Nullable SentryGraphqlInstrumentation.BeforeSpanCallback beforeSpan,
      final @NotNull SentrySubscriptionHandler subscriptionHandler,
      final boolean captureRequestBodyForNonSubscriptions,
      final @NotNull List<String> ignoredErrorTypes) {
    this(
        beforeSpan,
        subscriptionHandler,
        new ExceptionReporter(captureRequestBodyForNonSubscriptions),
        ignoredErrorTypes);
  }

  @TestOnly
  public SentryInstrumentation(
      final @Nullable SentryGraphqlInstrumentation.BeforeSpanCallback beforeSpan,
      final @NotNull SentrySubscriptionHandler subscriptionHandler,
      final @NotNull ExceptionReporter exceptionReporter,
      final @NotNull List<String> ignoredErrorTypes) {
    this.instrumentation =
        new SentryGraphqlInstrumentation(
            beforeSpan, subscriptionHandler, exceptionReporter, ignoredErrorTypes, TRACE_ORIGIN);
    SentryIntegrationPackageStorage.getInstance().addIntegration("GraphQL-v22");
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-graphql-22", BuildConfig.VERSION_NAME);
  }

  /**
   * @param subscriptionHandler can report subscription errors
   * @param captureRequestBodyForNonSubscriptions false if request bodies should not be captured by
   *     this integration for query and mutation operations. This can be used to prevent unnecessary
   *     work by not adding the request body when another integration will add it anyways, as is the
   *     case with our spring integration for WebMVC.
   */
  public SentryInstrumentation(
      final @NotNull SentrySubscriptionHandler subscriptionHandler,
      final boolean captureRequestBodyForNonSubscriptions) {
    this(null, subscriptionHandler, captureRequestBodyForNonSubscriptions);
  }

  @Override
  public @NotNull InstrumentationState createState(
      final @NotNull InstrumentationCreateStateParameters parameters) {
    return instrumentation.createState();
  }

  @Override
  public @Nullable InstrumentationContext<ExecutionResult> beginExecution(
      final @NotNull InstrumentationExecutionParameters parameters,
      final @NotNull InstrumentationState state) {
    final SentryGraphqlInstrumentation.TracingState tracingState =
        InstrumentationState.ofState(state);
    instrumentation.beginExecution(parameters, tracingState);
    return super.beginExecution(parameters, state);
  }

  @Override
  public @NotNull CompletableFuture<ExecutionResult> instrumentExecutionResult(
      final @NotNull ExecutionResult executionResult,
      final @NotNull InstrumentationExecutionParameters parameters,
      final @NotNull InstrumentationState state) {
    return super.instrumentExecutionResult(executionResult, parameters, state)
        .whenComplete(
            (result, exception) -> {
              instrumentation.instrumentExecutionResultComplete(parameters, result, exception);
            });
  }

  @Override
  public @Nullable InstrumentationContext<ExecutionResult> beginExecuteOperation(
      final @NotNull InstrumentationExecuteOperationParameters parameters,
      final @NotNull InstrumentationState state) {
    instrumentation.beginExecuteOperation(parameters);
    return super.beginExecuteOperation(parameters, state);
  }

  @Override
  @SuppressWarnings({"FutureReturnValueIgnored", "deprecation"})
  public @NotNull DataFetcher<?> instrumentDataFetcher(
      final @NotNull DataFetcher<?> dataFetcher,
      final @NotNull InstrumentationFieldFetchParameters parameters,
      final @NotNull InstrumentationState state) {
    final SentryGraphqlInstrumentation.TracingState tracingState =
        InstrumentationState.ofState(state);
    return instrumentation.instrumentDataFetcher(dataFetcher, parameters, tracingState);
  }

  /**
   * @deprecated please use {@link SentryGraphqlInstrumentation.BeforeSpanCallback}
   */
  @Deprecated
  @FunctionalInterface
  public interface BeforeSpanCallback extends SentryGraphqlInstrumentation.BeforeSpanCallback {}
}
