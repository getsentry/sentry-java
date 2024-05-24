package io.sentry.graphql;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.AstPrinter;
import graphql.schema.DataFetchingEnvironment;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Request;
import io.sentry.protocol.Response;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ExceptionReporter {
  private final boolean captureRequestBodyForNonSubscriptions;

  public ExceptionReporter(final boolean captureRequestBodyForNonSubscriptions) {
    this.captureRequestBodyForNonSubscriptions = captureRequestBodyForNonSubscriptions;
  }

  private static final @NotNull String MECHANISM_TYPE = "GraphqlInstrumentation";

  public void captureThrowable(
      final @NotNull Throwable throwable,
      final @NotNull ExceptionDetails exceptionDetails,
      final @Nullable ExecutionResult result) {
    final @NotNull IScopes scopes = exceptionDetails.getScopes();
    final @NotNull Mechanism mechanism = new Mechanism();
    mechanism.setType(MECHANISM_TYPE);
    mechanism.setHandled(false);
    final @NotNull Throwable mechanismException =
        new ExceptionMechanismException(mechanism, throwable, Thread.currentThread());
    final @NotNull SentryEvent event = new SentryEvent(mechanismException);
    event.setLevel(SentryLevel.FATAL);

    final @NotNull Hint hint = new Hint();
    setRequestDetailsOnEvent(scopes, exceptionDetails, event);

    if (result != null && isAllowedToAttachBody(scopes)) {
      final @NotNull Response response = new Response();
      final @NotNull Map<String, Object> responseBody = result.toSpecification();
      response.setData(responseBody);
      event.getContexts().setResponse(response);
    }

    scopes.captureEvent(event, hint);
  }

  private boolean isAllowedToAttachBody(final @NotNull IScopes scopes) {
    final @NotNull SentryOptions options = scopes.getOptions();
    return options.isSendDefaultPii()
        && !SentryOptions.RequestSize.NONE.equals(options.getMaxRequestBodySize());
  }

  private void setRequestDetailsOnEvent(
      final @NotNull IScopes scopes,
      final @NotNull ExceptionDetails exceptionDetails,
      final @NotNull SentryEvent event) {
    scopes.configureScope(
        (scope) -> {
          final @Nullable Request scopeRequest = scope.getRequest();
          final @NotNull Request request = scopeRequest == null ? new Request() : scopeRequest;
          setDetailsOnRequest(scopes, exceptionDetails, request);
          event.setRequest(request);
        });
  }

  private void setDetailsOnRequest(
      final @NotNull IScopes scopes,
      final @NotNull ExceptionDetails exceptionDetails,
      final @NotNull Request request) {
    request.setApiTarget("graphql");

    if (isAllowedToAttachBody(scopes)
        && (exceptionDetails.isSubscription() || captureRequestBodyForNonSubscriptions)) {
      final @NotNull Map<String, Object> data = new HashMap<>();

      data.put("query", exceptionDetails.getQuery());

      final @Nullable Map<String, Object> variables = exceptionDetails.getVariables();
      if (variables != null && !variables.isEmpty()) {
        data.put("variables", variables);
      }

      // for Spring HTTP this will be replaced by RequestBodyExtractingEventProcessor
      // for non subscription (websocket) errors
      request.setData(data);
    }
  }

  public static final class ExceptionDetails {

    private final @NotNull IScopes scopes;
    private final @Nullable InstrumentationExecutionParameters instrumentationExecutionParameters;
    private final @Nullable DataFetchingEnvironment dataFetchingEnvironment;

    private final boolean isSubscription;

    public ExceptionDetails(
        final @NotNull IScopes scopes,
        final @Nullable InstrumentationExecutionParameters instrumentationExecutionParameters,
        final boolean isSubscription) {
      this.scopes = scopes;
      this.instrumentationExecutionParameters = instrumentationExecutionParameters;
      dataFetchingEnvironment = null;
      this.isSubscription = isSubscription;
    }

    public ExceptionDetails(
        final @NotNull IScopes scopes,
        final @Nullable DataFetchingEnvironment dataFetchingEnvironment,
        final boolean isSubscription) {
      this.scopes = scopes;
      this.dataFetchingEnvironment = dataFetchingEnvironment;
      instrumentationExecutionParameters = null;
      this.isSubscription = isSubscription;
    }

    public @Nullable String getQuery() {
      if (instrumentationExecutionParameters != null) {
        return instrumentationExecutionParameters.getQuery();
      }
      if (dataFetchingEnvironment != null) {
        return AstPrinter.printAst(dataFetchingEnvironment.getDocument());
      }
      return null;
    }

    public @Nullable Map<String, Object> getVariables() {
      if (instrumentationExecutionParameters != null) {
        return instrumentationExecutionParameters.getVariables();
      }
      if (dataFetchingEnvironment != null) {
        return dataFetchingEnvironment.getVariables();
      }
      return null;
    }

    public boolean isSubscription() {
      return isSubscription;
    }

    /**
     * @deprecated please use {@link ExceptionDetails#getScopes()} instead.
     */
    @Deprecated
    public @NotNull IScopes getHub() {
      return scopes;
    }

    public @NotNull IScopes getScopes() {
      return scopes;
    }
  }
}
