package io.sentry;

import io.sentry.Stack.StackItem;
import io.sentry.hints.SessionEndHint;
import io.sentry.hints.SessionStartHint;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.util.ExceptionUtils;
import io.sentry.util.Objects;
import io.sentry.util.Pair;
import java.io.Closeable;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Hub implements IHub {
  private volatile @NotNull SentryId lastEventId;
  private final @NotNull SentryOptions options;
  private volatile boolean isEnabled;
  private final @NotNull Stack stack;
  private final @NotNull TracesSampler tracesSampler;
  private final @NotNull Map<Throwable, Pair<ISpan, String>> throwableToSpan =
      Collections.synchronizedMap(new WeakHashMap<>());

  public Hub(final @NotNull SentryOptions options) {
    this(options, createRootStackItem(options));

    // Integrations are no longer registered on Hub ctor, but on Sentry.init
  }

  private Hub(final @NotNull SentryOptions options, final @NotNull Stack stack) {
    validateOptions(options);

    this.options = options;
    this.tracesSampler = new TracesSampler(options);
    this.stack = stack;
    this.lastEventId = SentryId.EMPTY_ID;

    // Integrations will use this Hub instance once registered.
    // Make sure Hub ready to be used then.
    this.isEnabled = true;
  }

  private Hub(final @NotNull SentryOptions options, final @NotNull StackItem rootStackItem) {
    this(options, new Stack(options.getLogger(), rootStackItem));
  }

  private static void validateOptions(final @NotNull SentryOptions options) {
    Objects.requireNonNull(options, "SentryOptions is required.");
    if (options.getDsn() == null || options.getDsn().isEmpty()) {
      throw new IllegalArgumentException(
          "Hub requires a DSN to be instantiated. Considering using the NoOpHub is no DSN is available.");
    }
  }

  private static StackItem createRootStackItem(final @NotNull SentryOptions options) {
    validateOptions(options);
    final Scope scope = new Scope(options);
    final ISentryClient client = new SentryClient(options);
    return new StackItem(options, client, scope);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public @NotNull SentryId captureEvent(
      final @NotNull SentryEvent event, final @Nullable Object hint) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING, "Instance is disabled and this 'captureEvent' call is a no-op.");
    } else if (event == null) {
      options.getLogger().log(SentryLevel.WARNING, "captureEvent called with null parameter.");
    } else {
      try {
        assignTraceContext(event);
        final StackItem item = stack.peek();
        sentryId = item.getClient().captureEvent(event, item.getScope(), hint);
        this.lastEventId = sentryId;
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR, "Error while capturing event with id: " + event.getEventId(), e);
      }
    }
    return sentryId;
  }

  @Override
  public @NotNull SentryId captureMessage(
      final @NotNull String message, final @NotNull SentryLevel level) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureMessage' call is a no-op.");
    } else if (message == null) {
      options.getLogger().log(SentryLevel.WARNING, "captureMessage called with null parameter.");
    } else {
      try {
        final StackItem item = stack.peek();
        sentryId = item.getClient().captureMessage(message, level, item.getScope());
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, "Error while capturing message: " + message, e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull SentryId captureEnvelope(
      final @NotNull SentryEnvelope envelope, final @Nullable Object hint) {
    Objects.requireNonNull(envelope, "SentryEnvelope is required.");

    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureEnvelope' call is a no-op.");
    } else {
      try {
        final SentryId capturedEnvelopeId =
            stack.peek().getClient().captureEnvelope(envelope, hint);
        if (capturedEnvelopeId != null) {
          sentryId = capturedEnvelopeId;
        }
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, "Error while capturing envelope.", e);
      }
    }
    return sentryId;
  }

  @Override
  public @NotNull SentryId captureException(
      final @NotNull Throwable throwable, final @Nullable Object hint) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureException' call is a no-op.");
    } else if (throwable == null) {
      options.getLogger().log(SentryLevel.WARNING, "captureException called with null parameter.");
    } else {
      try {
        final StackItem item = stack.peek();
        final SentryEvent event = new SentryEvent(throwable);
        assignTraceContext(event);
        sentryId = item.getClient().captureEvent(event, item.getScope(), hint);
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR, "Error while capturing exception: " + throwable.getMessage(), e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
  }

  private void assignTraceContext(final @NotNull SentryEvent event) {
    if (options.isTracingEnabled() && event.getThrowable() != null) {
      final Pair<ISpan, String> pair =
          throwableToSpan.get(ExceptionUtils.findRootCause(event.getThrowable()));
      if (pair != null) {
        final ISpan span = pair.getFirst();
        if (event.getContexts().getTrace() == null && span != null) {
          event.getContexts().setTrace(span.getSpanContext());
        }
        final String transactionName = pair.getSecond();
        if (event.getTransaction() == null && transactionName != null) {
          event.setTransaction(transactionName);
        }
      }
    }
  }

  @Override
  public void captureUserFeedback(final @NotNull UserFeedback userFeedback) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureUserFeedback' call is a no-op.");
    } else {
      try {
        final StackItem item = stack.peek();
        item.getClient().captureUserFeedback(userFeedback);
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "Error while capturing captureUserFeedback: " + userFeedback.toString(),
                e);
      }
    }
  }

  @Override
  public void startSession() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING, "Instance is disabled and this 'startSession' call is a no-op.");
    } else {
      final StackItem item = this.stack.peek();
      final Scope.SessionPair pair = item.getScope().startSession();
      if (pair != null) {
        // TODO: add helper overload `captureSessions` to pass a list of sessions and submit a
        // single envelope
        // Or create the envelope here with both items and call `captureEnvelope`
        if (pair.getPrevious() != null) {
          item.getClient().captureSession(pair.getPrevious(), new SessionEndHint());
        }

        item.getClient().captureSession(pair.getCurrent(), new SessionStartHint());
      } else {
        options.getLogger().log(SentryLevel.WARNING, "Session could not be started.");
      }
    }
  }

  @Override
  public void endSession() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'endSession' call is a no-op.");
    } else {
      final StackItem item = this.stack.peek();
      final Session previousSession = item.getScope().endSession();
      if (previousSession != null) {
        item.getClient().captureSession(previousSession, new SessionEndHint());
      }
    }
  }

  @Override
  public void close() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'close' call is a no-op.");
    } else {
      try {
        for (Integration integration : options.getIntegrations()) {
          if (integration instanceof Closeable) {
            ((Closeable) integration).close();
          }
        }
        options.getExecutorService().close(options.getShutdownTimeout());

        // Close the top-most client
        final StackItem item = stack.peek();
        // TODO: should we end session before closing client?
        item.getClient().close();
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, "Error while closing the Hub.", e);
      }
      isEnabled = false;
    }
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb, final @Nullable Object hint) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'addBreadcrumb' call is a no-op.");
    } else if (breadcrumb == null) {
      options.getLogger().log(SentryLevel.WARNING, "addBreadcrumb called with null parameter.");
    } else {
      stack.peek().getScope().addBreadcrumb(breadcrumb, hint);
    }
  }

  @Override
  public void setLevel(final @Nullable SentryLevel level) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setLevel' call is a no-op.");
    } else {
      stack.peek().getScope().setLevel(level);
    }
  }

  @Override
  public void setTransaction(final @Nullable String transaction) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'setTransaction' call is a no-op.");
    } else if (transaction != null) {
      stack.peek().getScope().setTransaction(transaction);
    } else {
      options.getLogger().log(SentryLevel.WARNING, "Transaction cannot be null");
    }
  }

  @Override
  public void setUser(final @Nullable User user) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setUser' call is a no-op.");
    } else {
      stack.peek().getScope().setUser(user);
    }
  }

  @Override
  public void setFingerprint(final @NotNull List<String> fingerprint) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'setFingerprint' call is a no-op.");
    } else if (fingerprint == null) {
      options.getLogger().log(SentryLevel.WARNING, "setFingerprint called with null parameter.");
    } else {
      stack.peek().getScope().setFingerprint(fingerprint);
    }
  }

  @Override
  public void clearBreadcrumbs() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'clearBreadcrumbs' call is a no-op.");
    } else {
      stack.peek().getScope().clearBreadcrumbs();
    }
  }

  @Override
  public void setTag(final @NotNull String key, final @NotNull String value) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setTag' call is a no-op.");
    } else if (key == null || value == null) {
      options.getLogger().log(SentryLevel.WARNING, "setTag called with null parameter.");
    } else {
      stack.peek().getScope().setTag(key, value);
    }
  }

  @Override
  public void removeTag(final @NotNull String key) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'removeTag' call is a no-op.");
    } else if (key == null) {
      options.getLogger().log(SentryLevel.WARNING, "removeTag called with null parameter.");
    } else {
      stack.peek().getScope().removeTag(key);
    }
  }

  @Override
  public void setExtra(final @NotNull String key, final @NotNull String value) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setExtra' call is a no-op.");
    } else if (key == null || value == null) {
      options.getLogger().log(SentryLevel.WARNING, "setExtra called with null parameter.");
    } else {
      stack.peek().getScope().setExtra(key, value);
    }
  }

  @Override
  public void removeExtra(final @NotNull String key) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'removeExtra' call is a no-op.");
    } else if (key == null) {
      options.getLogger().log(SentryLevel.WARNING, "removeExtra called with null parameter.");
    } else {
      stack.peek().getScope().removeExtra(key);
    }
  }

  @Override
  public @NotNull SentryId getLastEventId() {
    return lastEventId;
  }

  @Override
  public void pushScope() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'pushScope' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      final StackItem newItem =
          new StackItem(options, item.getClient(), new Scope(item.getScope()));
      stack.push(newItem);
    }
  }

  @Override
  public @NotNull SentryOptions getOptions() {
    return this.stack.peek().getOptions();
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return SentryCrashLastRunState.getInstance()
        .isCrashedLastRun(options.getCacheDirPath(), !options.isEnableAutoSessionTracking());
  }

  @Override
  public void popScope() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'popScope' call is a no-op.");
    } else {
      stack.pop();
    }
  }

  @Override
  public void withScope(final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'withScope' call is a no-op.");
    } else {
      pushScope();
      try {
        callback.run(stack.peek().getScope());
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, "Error in the 'withScope' callback.", e);
      }
      popScope();
    }
  }

  @Override
  public void configureScope(final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'configureScope' call is a no-op.");
    } else {
      try {
        callback.run(stack.peek().getScope());
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, "Error in the 'configureScope' callback.", e);
      }
    }
  }

  @Override
  public void bindClient(final @NotNull ISentryClient client) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'bindClient' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      if (client != null) {
        options.getLogger().log(SentryLevel.DEBUG, "New client bound to scope.");
        item.setClient(client);
      } else {
        options.getLogger().log(SentryLevel.DEBUG, "NoOp client bound to scope.");
        item.setClient(NoOpSentryClient.getInstance());
      }
    }
  }

  @Override
  public void flush(long timeoutMillis) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'flush' call is a no-op.");
    } else {
      try {
        stack.peek().getClient().flush(timeoutMillis);
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, "Error in the 'client.flush'.", e);
      }
    }
  }

  @Override
  public @NotNull IHub clone() {
    if (!isEnabled()) {
      options.getLogger().log(SentryLevel.WARNING, "Disabled Hub cloned.");
    }
    // Clone will be invoked in parallel
    return new Hub(this.options, new Stack(this.stack));
  }

  @ApiStatus.Internal
  @Override
  public @NotNull SentryId captureTransaction(
      final @NotNull SentryTransaction transaction,
      final @Nullable TraceState traceState,
      final @Nullable Object hint) {
    Objects.requireNonNull(transaction, "transaction is required");

    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureTransaction' call is a no-op.");
    } else {
      if (!transaction.isFinished()) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Transaction: %s is not finished and this 'captureTransaction' call is a no-op.",
                transaction.getEventId());
      } else {
        if (!Boolean.TRUE.equals(transaction.isSampled())) {
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Transaction %s was dropped due to sampling decision.",
                  transaction.getEventId());
        } else {
          StackItem item = null;
          try {
            item = stack.peek();
            sentryId =
                item.getClient().captureTransaction(transaction, traceState, item.getScope(), hint);
          } catch (Exception e) {
            options
                .getLogger()
                .log(
                    SentryLevel.ERROR,
                    "Error while capturing transaction with id: " + transaction.getEventId(),
                    e);
          }
        }
      }
    }
    return sentryId;
  }

  @Override
  public @NotNull ITransaction startTransaction(
      final @NotNull TransactionContext transactionContext,
      final @Nullable CustomSamplingContext customSamplingContext,
      final boolean bindToScope) {
    return createTransaction(
        transactionContext, customSamplingContext, bindToScope, null, false, null);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContext,
      @Nullable CustomSamplingContext customSamplingContext,
      boolean bindToScope,
      @Nullable Date startTimestamp) {
    return createTransaction(
        transactionContext, customSamplingContext, bindToScope, startTimestamp, false, null);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull ITransaction startTransaction(
      final @NotNull TransactionContext transactionContexts,
      final @Nullable CustomSamplingContext customSamplingContext,
      final boolean bindToScope,
      final @Nullable Date startTimestamp,
      final boolean waitForChildren,
      final @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    return createTransaction(
        transactionContexts,
        customSamplingContext,
        bindToScope,
        startTimestamp,
        waitForChildren,
        transactionFinishedCallback);
  }

  private @NotNull ITransaction createTransaction(
      final @NotNull TransactionContext transactionContext,
      final @Nullable CustomSamplingContext customSamplingContext,
      final boolean bindToScope,
      final @Nullable Date startTimestamp,
      final boolean waitForChildren,
      final @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    Objects.requireNonNull(transactionContext, "transactionContext is required");

    ITransaction transaction;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'startTransaction' returns a no-op.");
      transaction = NoOpTransaction.getInstance();
    } else if (!options.isTracingEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO, "Tracing is disabled and this 'startTransaction' returns a no-op.");
      transaction = NoOpTransaction.getInstance();
    } else {
      final SamplingContext samplingContext =
          new SamplingContext(transactionContext, customSamplingContext);
      boolean samplingDecision = tracesSampler.sample(samplingContext);
      transactionContext.setSampled(samplingDecision);

      transaction =
          new SentryTracer(
              transactionContext,
              this,
              startTimestamp,
              waitForChildren,
              transactionFinishedCallback);
    }
    if (bindToScope) {
      configureScope(scope -> scope.setTransaction(transaction));
    }
    return transaction;
  }

  @Override
  public @Nullable SentryTraceHeader traceHeaders() {
    SentryTraceHeader traceHeader = null;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING, "Instance is disabled and this 'traceHeaders' call is a no-op.");
    } else {
      final ISpan span = stack.peek().getScope().getSpan();
      if (span != null) {
        traceHeader = span.toSentryTrace();
      }
    }
    return traceHeader;
  }

  @Override
  public @Nullable ISpan getSpan() {
    ISpan span = null;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'getSpan' call is a no-op.");
    } else {
      span = stack.peek().getScope().getSpan();
    }
    return span;
  }

  @Override
  @ApiStatus.Internal
  public void setSpanContext(
      final @NotNull Throwable throwable,
      final @NotNull ISpan span,
      final @NotNull String transactionName) {
    Objects.requireNonNull(throwable, "throwable is required");
    Objects.requireNonNull(span, "span is required");
    Objects.requireNonNull(transactionName, "transactionName is required");
    // to match any cause, span context is always attached to the root cause of the exception
    final Throwable rootCause = ExceptionUtils.findRootCause(throwable);
    // the most inner span should be assigned to a throwable
    if (!throwableToSpan.containsKey(rootCause)) {
      throwableToSpan.put(rootCause, new Pair<>(span, transactionName));
    }
  }

  @Nullable
  SpanContext getSpanContext(final @NotNull Throwable throwable) {
    Objects.requireNonNull(throwable, "throwable is required");
    final Throwable rootCause = ExceptionUtils.findRootCause(throwable);
    final Pair<ISpan, String> pair = this.throwableToSpan.get(rootCause);
    if (pair != null) {
      final ISpan span = pair.getFirst();
      if (span != null) {
        return span.getSpanContext();
      }
    }
    return null;
  }
}
