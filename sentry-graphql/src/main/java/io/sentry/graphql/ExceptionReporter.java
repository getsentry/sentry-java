package io.sentry.graphql;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.AstPrinter;
import graphql.schema.DataFetchingEnvironment;
import io.sentry.Hint;
import io.sentry.IHub;
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
    final @NotNull IHub hub = exceptionDetails.getHub();
    final @NotNull Mechanism mechanism = new Mechanism();
    mechanism.setType(MECHANISM_TYPE);
    mechanism.setHandled(false);
    final @NotNull Throwable mechanismException =
        new ExceptionMechanismException(mechanism, throwable, Thread.currentThread());
    final @NotNull SentryEvent event = new SentryEvent(mechanismException);
    event.setLevel(SentryLevel.FATAL);

    final @NotNull Hint hint = new Hint();
    setRequestDetailsOnEvent(hub, exceptionDetails, event);

    if (result != null && isAllowedToAttachBody(hub)) {
      final @NotNull Response response = new Response();
      final @NotNull Map<String, Object> responseBody = result.toSpecification();
      response.setData(responseBody);
      event.getContexts().setResponse(response);
    }

    hub.captureEvent(event, hint);
  }

  private boolean isAllowedToAttachBody(final @NotNull IHub hub) {
    final @NotNull SentryOptions options = hub.getOptions();
    return options.isSendDefaultPii()
        && !SentryOptions.RequestSize.NONE.equals(options.getMaxRequestBodySize());
  }

  private void setRequestDetailsOnEvent(
      final @NotNull IHub hub,
      final @NotNull ExceptionDetails exceptionDetails,
      final @NotNull SentryEvent event) {
    hub.configureScope(
        (scope) -> {
          final @Nullable Request scopeRequest = scope.getRequest();
          final @NotNull Request request = scopeRequest == null ? new Request() : scopeRequest;
          setDetailsOnRequest(hub, exceptionDetails, request);
          event.setRequest(request);
        });
  }

  private void setDetailsOnRequest(
      final @NotNull IHub hub,
      final @NotNull ExceptionDetails exceptionDetails,
      final @NotNull Request request) {
    request.setApiTarget("graphql");

    if (isAllowedToAttachBody(hub)
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

    private final @NotNull IHub hub;
    private final @Nullable InstrumentationExecutionParameters instrumentationExecutionParameters;
    private final @Nullable DataFetchingEnvironment dataFetchingEnvironment;

    private final boolean isSubscription;

    public ExceptionDetails(
        final @NotNull IHub hub,
        final @Nullable InstrumentationExecutionParameters instrumentationExecutionParameters,
        final boolean isSubscription) {
      this.hub = hub;
      this.instrumentationExecutionParameters = instrumentationExecutionParameters;
      dataFetchingEnvironment = null;
      this.isSubscription = isSubscription;
    }

    public ExceptionDetails(
        final @NotNull IHub hub,
        final @Nullable DataFetchingEnvironment dataFetchingEnvironment,
        final boolean isSubscription) {
      this.hub = hub;
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

    public @NotNull IHub getHub() {
      return hub;
    }
  }
}
