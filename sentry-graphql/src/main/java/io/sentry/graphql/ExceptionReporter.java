package io.sentry.graphql;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.AstPrinter;
import graphql.schema.DataFetchingEnvironment;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Request;
import io.sentry.protocol.Response;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExceptionReporter {
  private final boolean isSpring;

  public ExceptionReporter(final boolean isSpring) {
    this.isSpring = isSpring;
  }

  private static final @NotNull String MECHANISM_TYPE = "GraphqlInstrumentation";

  public void captureThrowable(
      final @NotNull Throwable throwable,
      final @NotNull ExceptionDetails exceptionDetails,
      final @Nullable ExecutionResult result) {
    final @NotNull IHub hub = exceptionDetails.getHub();
    final Mechanism mechanism = new Mechanism();
    mechanism.setType(MECHANISM_TYPE);
    mechanism.setHandled(false);
    final Throwable mechanismException =
        new ExceptionMechanismException(mechanism, throwable, Thread.currentThread());
    final SentryEvent event = new SentryEvent(mechanismException);
    event.setLevel(SentryLevel.FATAL);

    final Hint hint = new Hint();
    setRequestDetailsOnEvent(hub, exceptionDetails, event);

    if (result != null) {
      @NotNull Response response = new Response();
      Map<String, Object> responseBody = result.toSpecification();
      response.setData(responseBody);
      event.getContexts().setResponse(response);
    }

    hub.captureEvent(event, hint);
  }

  private void setRequestDetailsOnEvent(
      final @NotNull IHub hub,
      final @NotNull ExceptionDetails exceptionDetails,
      final @NotNull SentryEvent event) {
    hub.configureScope(
        (scope) -> {
          final @Nullable Request scopeRequest = scope.getRequest();
          if (scopeRequest != null) {
            setDetailsOnRequest(hub, exceptionDetails, scopeRequest);
            event.setRequest(scopeRequest);
          } else {
            Request newRequest = new Request();
            setDetailsOnRequest(hub, exceptionDetails, newRequest);
            event.setRequest(newRequest);
          }
        });
  }

  private void setDetailsOnRequest(
      final @NotNull IHub hub,
      final @NotNull ExceptionDetails exceptionDetails,
      final @NotNull Request request) {
    request.setApiTarget("graphql");

    if (exceptionDetails.isSubscription() || !isSpring) {
      final @NotNull Map<String, Object> data = new HashMap<>();

      data.put("data", exceptionDetails.getQuery());

      if (hub.getOptions().isSendDefaultPii()) {
        data.put("variables", exceptionDetails.getVariables());
      }

      // for Spring this will be replaced by RequestBodyExtractingEventProcessor
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
        final @Nullable InstrumentationExecutionParameters instrumentationExecutionParameters) {
      this.hub = hub;
      this.instrumentationExecutionParameters = instrumentationExecutionParameters;
      dataFetchingEnvironment = null;
      isSubscription = false;
    }

    public ExceptionDetails(
        final @NotNull IHub hub, final @Nullable DataFetchingEnvironment dataFetchingEnvironment) {
      this.hub = hub;
      this.dataFetchingEnvironment = dataFetchingEnvironment;
      instrumentationExecutionParameters = null;
      isSubscription = true;
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

    public IHub getHub() {
      return hub;
    }
  }
}
