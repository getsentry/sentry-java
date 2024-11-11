package io.sentry.graphql;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import io.sentry.SentryIntegrationPackageStorage;
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

  private static final String TRACE_ORIGIN = "auto.graphql.graphql";
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
    SentryIntegrationPackageStorage.getInstance().addIntegration("GraphQL");
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-graphql", BuildConfig.VERSION_NAME);
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
  public @NotNull InstrumentationState createState() {
    return instrumentation.createState();
  }

  @Override
  public @NotNull InstrumentationContext<ExecutionResult> beginExecution(
      final @NotNull InstrumentationExecutionParameters parameters) {
    final SentryGraphqlInstrumentation.TracingState tracingState =
        parameters.getInstrumentationState();
    instrumentation.beginExecution(parameters, tracingState);
    return super.beginExecution(parameters);
  }

  @Override
  public CompletableFuture<ExecutionResult> instrumentExecutionResult(
      ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
    return super.instrumentExecutionResult(executionResult, parameters)
        .whenComplete(
            (result, exception) -> {
              instrumentation.instrumentExecutionResultComplete(parameters, result, exception);
            });
  }

  @Override
  public @NotNull InstrumentationContext<ExecutionResult> beginExecuteOperation(
      final @NotNull InstrumentationExecuteOperationParameters parameters) {
    instrumentation.beginExecuteOperation(parameters);
    return super.beginExecuteOperation(parameters);
  }

  @Override
  @SuppressWarnings({"FutureReturnValueIgnored", "deprecation"})
  public @NotNull DataFetcher<?> instrumentDataFetcher(
      final @NotNull DataFetcher<?> dataFetcher,
      final @NotNull InstrumentationFieldFetchParameters parameters) {
    final SentryGraphqlInstrumentation.TracingState tracingState =
        parameters.getInstrumentationState();
    return instrumentation.instrumentDataFetcher(dataFetcher, parameters, tracingState);
  }

  /**
   * @deprecated please use {@link SentryGraphqlInstrumentation.BeforeSpanCallback}
   */
  @Deprecated
  @FunctionalInterface
  public interface BeforeSpanCallback extends SentryGraphqlInstrumentation.BeforeSpanCallback {}
}
