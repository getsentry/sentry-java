package io.sentry;

import io.sentry.clientreport.DiscardReason;
import io.sentry.hints.SessionEndHint;
import io.sentry.hints.SessionStartHint;
import io.sentry.metrics.LocalMetricsAggregator;
import io.sentry.metrics.MetricsApi;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.transport.RateLimiter;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import io.sentry.util.TracingUtils;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Scopes implements IScopes, MetricsApi.IMetricsInterface {

  private final @NotNull IScope scope;
  private final @NotNull IScope isolationScope;
  // TODO just for debugging
  @SuppressWarnings("UnusedVariable")
  private final @Nullable Scopes parentScopes;

  private final @NotNull String creator;

  // TODO should this be set on all scopes (global, isolation, current)?
  private final @NotNull SentryOptions options;
  private volatile boolean isEnabled;
  private final @NotNull TracesSampler tracesSampler;
  private final @NotNull TransactionPerformanceCollector transactionPerformanceCollector;
  private final @NotNull MetricsApi metricsApi;

  Scopes(
      final @NotNull IScope scope,
      final @NotNull IScope isolationScope,
      final @NotNull SentryOptions options,
      final @NotNull String creator) {
    this(scope, isolationScope, null, options, creator);
  }

  private Scopes(
      final @NotNull IScope scope,
      final @NotNull IScope isolationScope,
      final @Nullable Scopes parentScopes,
      final @NotNull SentryOptions options,
      final @NotNull String creator) {
    validateOptions(options);

    this.scope = scope;
    this.isolationScope = isolationScope;
    this.parentScopes = parentScopes;
    this.creator = creator;
    this.options = options;
    this.tracesSampler = new TracesSampler(options);
    this.transactionPerformanceCollector = options.getTransactionPerformanceCollector();

    this.isEnabled = true;

    this.metricsApi = new MetricsApi(this);
  }

  public @NotNull String getCreator() {
    return creator;
  }

  // TODO add to IScopes interface
  public @NotNull IScope getScope() {
    return scope;
  }

  // TODO add to IScopes interface
  public @NotNull IScope getIsolationScope() {
    return isolationScope;
  }

  // TODO add to IScopes interface?
  public @Nullable Scopes getParent() {
    return parentScopes;
  }

  // TODO add to IScopes interface?
  public boolean isAncestorOf(final @Nullable Scopes otherScopes) {
    if (otherScopes == null) {
      return false;
    }

    if (this == otherScopes) {
      return true;
    }

    final @Nullable Scopes parent = otherScopes.getParent();
    if (parent != null) {
      return isAncestorOf(parent);
    }

    return false;
  }

  // TODO add to IScopes interface
  public @NotNull Scopes forkedScopes(final @NotNull String creator) {
    return new Scopes(scope.clone(), isolationScope.clone(), this, options, creator);
  }

  // TODO add to IScopes interface
  public @NotNull Scopes forkedCurrentScope(final @NotNull String creator) {
    IScope clone = scope.clone();
    // TODO should use isolation scope
    //    return new Scopes(clone, isolationScope, this, options, creator);
    return new Scopes(clone, clone, this, options, creator);
  }

  //  // TODO in Sentry.init?
  //  public static Scopes forkedRoots(final @NotNull SentryOptions options, final @NotNull String
  // creator) {
  //    return new Scopes(ROOT_SCOPE.clone(), ROOT_ISOLATION_SCOPE.clone(), options, creator);
  //  }

  // TODO always read from root scope?
  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Hint hint) {
    return captureEventInternal(event, hint, null);
  }

  @Override
  public @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return captureEventInternal(event, hint, callback);
  }

  private @NotNull SentryId captureEventInternal(
      final @NotNull SentryEvent event,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback scopeCallback) {
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
        final IScope localScope = buildLocalScope(getCombinedScopeView(), scopeCallback);

        sentryId = getClient().captureEvent(event, localScope, hint);
        updateLastEventId(sentryId);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR, "Error while capturing event with id: " + event.getEventId(), e);
      }
    }
    return sentryId;
  }

  private @NotNull ISentryClient getClient() {
    return getCombinedScopeView().getClient();
  }

  private void assignTraceContext(final @NotNull SentryEvent event) {
    Sentry.getGlobalScope().assignTraceContext(event);
  }

  private IScope buildLocalScope(
      final @NotNull IScope parentScope, final @Nullable ScopeCallback callback) {
    if (callback != null) {
      try {
        final IScope localScope = parentScope.clone();
        callback.run(localScope);
        return localScope;
      } catch (Throwable t) {
        options.getLogger().log(SentryLevel.ERROR, "Error in the 'ScopeCallback' callback.", t);
      }
    }
    return parentScope;
  }

  @Override
  public @NotNull SentryId captureMessage(
      final @NotNull String message, final @NotNull SentryLevel level) {
    return captureMessageInternal(message, level, null);
  }

  @Override
  public @NotNull SentryId captureMessage(
      final @NotNull String message,
      final @NotNull SentryLevel level,
      final @NotNull ScopeCallback callback) {
    return captureMessageInternal(message, level, callback);
  }

  private @NotNull SentryId captureMessageInternal(
      final @NotNull String message,
      final @NotNull SentryLevel level,
      final @Nullable ScopeCallback scopeCallback) {
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
        final IScope localScope = buildLocalScope(getCombinedScopeView(), scopeCallback);

        sentryId = getClient().captureMessage(message, level, localScope);
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Error while capturing message: " + message, e);
      }
    }
    updateLastEventId(sentryId);
    return sentryId;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull SentryId captureEnvelope(
      final @NotNull SentryEnvelope envelope, final @Nullable Hint hint) {
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
        final SentryId capturedEnvelopeId = getClient().captureEnvelope(envelope, hint);
        if (capturedEnvelopeId != null) {
          sentryId = capturedEnvelopeId;
        }
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Error while capturing envelope.", e);
      }
    }
    return sentryId;
  }

  @Override
  public @NotNull SentryId captureException(
      final @NotNull Throwable throwable, final @Nullable Hint hint) {
    return captureExceptionInternal(throwable, hint, null);
  }

  @Override
  public @NotNull SentryId captureException(
      final @NotNull Throwable throwable,
      final @Nullable Hint hint,
      final @NotNull ScopeCallback callback) {

    return captureExceptionInternal(throwable, hint, callback);
  }

  private @NotNull SentryId captureExceptionInternal(
      final @NotNull Throwable throwable,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback scopeCallback) {
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
        final SentryEvent event = new SentryEvent(throwable);
        assignTraceContext(event);

        final IScope localScope = buildLocalScope(getCombinedScopeView(), scopeCallback);

        sentryId = getClient().captureEvent(event, localScope, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR, "Error while capturing exception: " + throwable.getMessage(), e);
      }
    }
    updateLastEventId(sentryId);
    return sentryId;
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
        getClient().captureUserFeedback(userFeedback);
      } catch (Throwable e) {
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
      final Scope.SessionPair pair = getCombinedScopeView().startSession();
      if (pair != null) {
        // TODO: add helper overload `captureSessions` to pass a list of sessions and submit a
        // single envelope
        // Or create the envelope here with both items and call `captureEnvelope`
        if (pair.getPrevious() != null) {
          final Hint hint = HintUtils.createWithTypeCheckHint(new SessionEndHint());

          getClient().captureSession(pair.getPrevious(), hint);
        }

        final Hint hint = HintUtils.createWithTypeCheckHint(new SessionStartHint());

        getClient().captureSession(pair.getCurrent(), hint);
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
      final Session previousSession = getCombinedScopeView().endSession();
      if (previousSession != null) {
        final Hint hint = HintUtils.createWithTypeCheckHint(new SessionEndHint());

        getClient().captureSession(previousSession, hint);
      }
    }
  }

  private IScope getCombinedScopeView() {
    // TODO combine global, isolation and current scope
    return scope;
  }

  @Override
  public void close() {
    close(false);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void close(final boolean isRestarting) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'close' call is a no-op.");
    } else {
      try {
        for (Integration integration : options.getIntegrations()) {
          if (integration instanceof Closeable) {
            try {
              ((Closeable) integration).close();
            } catch (IOException e) {
              options
                  .getLogger()
                  .log(SentryLevel.WARNING, "Failed to close the integration {}.", integration, e);
            }
          }
        }

        // TODO which scopes do we call this on? isolation and current scope?
        configureScope(scope -> scope.clear());
        options.getTransactionProfiler().close();
        options.getTransactionPerformanceCollector().close();
        final @NotNull ISentryExecutorService executorService = options.getExecutorService();
        if (isRestarting) {
          executorService.submit(() -> executorService.close(options.getShutdownTimeoutMillis()));
        } else {
          executorService.close(options.getShutdownTimeoutMillis());
        }

        // TODO: should we end session before closing client?
        getClient().close(isRestarting);
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Error while closing the Hub.", e);
      }
      isEnabled = false;
    }
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb, final @Nullable Hint hint) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'addBreadcrumb' call is a no-op.");
    } else if (breadcrumb == null) {
      options.getLogger().log(SentryLevel.WARNING, "addBreadcrumb called with null parameter.");
    } else {
      getDefaultWriteScope().addBreadcrumb(breadcrumb, hint);
    }
  }

  private IScope getDefaultConfigureScope() {
    // TODO configurable default scope via SentryOptions, Android = global or isolation, backend =
    // isolation
    return scope;
  }

  private IScope getDefaultWriteScope() {
    // TODO configurable default scope via SentryOptions, Android = global or isolation, backend =
    // isolation
    return getIsolationScope();
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, new Hint());
  }

  @Override
  public void setLevel(final @Nullable SentryLevel level) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setLevel' call is a no-op.");
    } else {
      getDefaultWriteScope().setLevel(level);
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
      getDefaultWriteScope().setTransaction(transaction);
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
      getDefaultWriteScope().setUser(user);
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
      getDefaultWriteScope().setFingerprint(fingerprint);
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
      getDefaultWriteScope().clearBreadcrumbs();
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
      getDefaultWriteScope().setTag(key, value);
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
      getDefaultWriteScope().removeTag(key);
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
      getDefaultWriteScope().setExtra(key, value);
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
      getDefaultWriteScope().removeExtra(key);
    }
  }

  private void updateLastEventId(final @NotNull SentryId lastEventId) {
    scope.setLastEventId(lastEventId);
    isolationScope.setLastEventId(lastEventId);
    getGlobalScope().setLastEventId(lastEventId);
  }

  // TODO add to IScopes interface
  public @NotNull IScope getGlobalScope() {
    // TODO should be:
    return Sentry.getGlobalScope();
    //    return scope;
  }

  @Override
  public @NotNull SentryId getLastEventId() {
    // TODO read all scopes here / read default scope?
    // returning scope.lastEventId isn't ideal because changed to child scope are not stored in
    // there
    return getGlobalScope().getLastEventId();
  }

  // TODO needs to be deprecated because there's no more stack
  // TODO needs to return a lifecycle token
  @Override
  public void pushScope() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'pushScope' call is a no-op.");
    } else {
      //      Scopes scopes = this.forkedScopes("pushScope");
      //      return scopes.makeCurrent();
    }
  }

  //  public SentryLifecycleToken makeCurrent() {
  //    // TODO store.set(this);
  //  }

  // TODO needs to be deprecated because there's no more stack
  @Override
  public void popScope() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'popScope' call is a no-op.");
    } else {
      // TODO how to remove fork?
      // TODO getParentScopes().makeCurrent()?
    }
  }

  // TODO lots of testing required to see how ThreadLocal is affected
  @Override
  public void withScope(final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      try {
        callback.run(NoOpScope.getInstance());
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Error in the 'withScope' callback.", e);
      }

    } else {
      Scopes forkedScopes = forkedScopes("withScope");
      // TODO should forkedScopes be made current inside callback?
      // TODO forkedScopes.makeCurrent()?
      try {
        callback.run(forkedScopes.getScope());
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Error in the 'withScope' callback.", e);
      }
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
        callback.run(getDefaultConfigureScope());
      } catch (Throwable e) {
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
      if (client != null) {
        options.getLogger().log(SentryLevel.DEBUG, "New client bound to scope.");
        getDefaultWriteScope().bindClient(client);
      } else {
        options.getLogger().log(SentryLevel.DEBUG, "NoOp client bound to scope.");
        getDefaultWriteScope().bindClient(NoOpSentryClient.getInstance());
      }
    }
  }

  @Override
  public boolean isHealthy() {
    return getClient().isHealthy();
  }

  @Override
  public void flush(long timeoutMillis) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'flush' call is a no-op.");
    } else {
      try {
        getClient().flush(timeoutMillis);
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Error in the 'client.flush'.", e);
      }
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public @NotNull IHub clone() {
    if (!isEnabled()) {
      options.getLogger().log(SentryLevel.WARNING, "Disabled Hub cloned.");
    }
    return new HubScopesWrapper(forkedScopes("scopes clone"));
  }

  @ApiStatus.Internal
  @Override
  public @NotNull SentryId captureTransaction(
      final @NotNull SentryTransaction transaction,
      final @Nullable TraceContext traceContext,
      final @Nullable Hint hint,
      final @Nullable ProfilingTraceData profilingTraceData) {
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
          if (options.getBackpressureMonitor().getDownsampleFactor() > 0) {
            options
                .getClientReportRecorder()
                .recordLostEvent(DiscardReason.BACKPRESSURE, DataCategory.Transaction);
          } else {
            options
                .getClientReportRecorder()
                .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Transaction);
          }
        } else {
          try {
            sentryId =
                getClient()
                    .captureTransaction(
                        transaction,
                        traceContext,
                        getCombinedScopeView(),
                        hint,
                        profilingTraceData);
          } catch (Throwable e) {
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
      final @NotNull TransactionOptions transactionOptions) {
    return createTransaction(transactionContext, transactionOptions);
  }

  private @NotNull ITransaction createTransaction(
      final @NotNull TransactionContext transactionContext,
      final @NotNull TransactionOptions transactionOptions) {
    Objects.requireNonNull(transactionContext, "transactionContext is required");

    ITransaction transaction;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'startTransaction' returns a no-op.");
      transaction = NoOpTransaction.getInstance();
    } else if (!options.getInstrumenter().equals(transactionContext.getInstrumenter())) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Returning no-op for instrumenter %s as the SDK has been configured to use instrumenter %s",
              transactionContext.getInstrumenter(),
              options.getInstrumenter());
      transaction = NoOpTransaction.getInstance();
    } else if (!options.isTracingEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO, "Tracing is disabled and this 'startTransaction' returns a no-op.");
      transaction = NoOpTransaction.getInstance();
    } else {
      final SamplingContext samplingContext =
          new SamplingContext(transactionContext, transactionOptions.getCustomSamplingContext());
      @NotNull TracesSamplingDecision samplingDecision = tracesSampler.sample(samplingContext);
      transactionContext.setSamplingDecision(samplingDecision);

      transaction =
          new SentryTracer(
              transactionContext, this, transactionOptions, transactionPerformanceCollector);

      // The listener is called only if the transaction exists, as the transaction is needed to
      // stop it
      if (samplingDecision.getSampled() && samplingDecision.getProfileSampled()) {
        final ITransactionProfiler transactionProfiler = options.getTransactionProfiler();
        // If the profiler is not running, we start and bind it here.
        if (!transactionProfiler.isRunning()) {
          transactionProfiler.start();
          transactionProfiler.bindTransaction(transaction);
        } else if (transactionOptions.isAppStartTransaction()) {
          // If the profiler is running and the current transaction is the app start, we bind it.
          transactionProfiler.bindTransaction(transaction);
        }
      }
    }
    if (transactionOptions.isBindToScope()) {
      configureScope(scope -> scope.setTransaction(transaction));
    }
    return transaction;
  }

  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  @Override
  public @Nullable SentryTraceHeader traceHeaders() {
    return getTraceparent();
  }

  @Override
  @ApiStatus.Internal
  public void setSpanContext(
      final @NotNull Throwable throwable,
      final @NotNull ISpan span,
      final @NotNull String transactionName) {
    Sentry.getGlobalScope().setSpanContext(throwable, span, transactionName);
  }

  //  // TODO this seems unused
  //  @Nullable
  //  SpanContext getSpanContext(final @NotNull Throwable throwable) {
  //    Objects.requireNonNull(throwable, "throwable is required");
  //    final Throwable rootCause = ExceptionUtils.findRootCause(throwable);
  //    final Pair<WeakReference<ISpan>, String> pair = this.throwableToSpan.get(rootCause);
  //    if (pair != null) {
  //      final WeakReference<ISpan> spanWeakRef = pair.getFirst();
  //      if (spanWeakRef != null) {
  //        final ISpan span = spanWeakRef.get();
  //        if (span != null) {
  //          return span.getSpanContext();
  //        }
  //      }
  //    }
  //    return null;
  //  }

  @Override
  public @Nullable ISpan getSpan() {
    ISpan span = null;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'getSpan' call is a no-op.");
    } else {
      span = getScope().getSpan();
    }
    return span;
  }

  @Override
  @ApiStatus.Internal
  public @Nullable ITransaction getTransaction() {
    ITransaction span = null;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'getTransaction' call is a no-op.");
    } else {
      span = getScope().getTransaction();
    }
    return span;
  }

  @Override
  public @NotNull SentryOptions getOptions() {
    return options;
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return SentryCrashLastRunState.getInstance()
        .isCrashedLastRun(options.getCacheDirPath(), !options.isEnableAutoSessionTracking());
  }

  @Override
  public void reportFullyDisplayed() {
    if (options.isEnableTimeToFullDisplayTracing()) {
      options.getFullyDisplayedReporter().reportFullyDrawn();
    }
  }

  @Override
  public @Nullable TransactionContext continueTrace(
      final @Nullable String sentryTrace, final @Nullable List<String> baggageHeaders) {
    @NotNull
    PropagationContext propagationContext =
        PropagationContext.fromHeaders(getOptions().getLogger(), sentryTrace, baggageHeaders);
    // TODO should this go on isolation scope?
    configureScope(
        (scope) -> {
          scope.setPropagationContext(propagationContext);
        });
    if (options.isTracingEnabled()) {
      return TransactionContext.fromPropagationContext(propagationContext);
    } else {
      return null;
    }
  }

  @Override
  public @Nullable SentryTraceHeader getTraceparent() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'getTraceparent' call is a no-op.");
    } else {
      final @Nullable TracingUtils.TracingHeaders headers =
          TracingUtils.trace(this, null, getSpan());
      if (headers != null) {
        return headers.getSentryTraceHeader();
      }
    }

    return null;
  }

  @Override
  public @Nullable BaggageHeader getBaggage() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'getBaggage' call is a no-op.");
    } else {
      final @Nullable TracingUtils.TracingHeaders headers =
          TracingUtils.trace(this, null, getSpan());
      if (headers != null) {
        return headers.getBaggageHeader();
      }
    }

    return null;
  }

  @Override
  @ApiStatus.Experimental
  public @NotNull SentryId captureCheckIn(final @NotNull CheckIn checkIn) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureCheckIn' call is a no-op.");
    } else {
      try {
        sentryId = getClient().captureCheckIn(checkIn, getCombinedScopeView(), null);
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Error while capturing check-in for slug", e);
      }
    }
    updateLastEventId(sentryId);
    return sentryId;
  }

  @ApiStatus.Internal
  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return getClient().getRateLimiter();
  }

  @Override
  public @NotNull MetricsApi metrics() {
    return metricsApi;
  }

  @Override
  public @NotNull IMetricsAggregator getMetricsAggregator() {
    return getClient().getMetricsAggregator();
  }

  @Override
  public @NotNull Map<String, String> getDefaultTagsForMetrics() {
    if (!options.isEnableDefaultTagsForMetrics()) {
      return Collections.emptyMap();
    }

    final @NotNull Map<String, String> tags = new HashMap<>();
    final @Nullable String release = options.getRelease();
    if (release != null) {
      tags.put("release", release);
    }

    final @Nullable String environment = options.getEnvironment();
    if (environment != null) {
      tags.put("environment", environment);
    }

    final @Nullable String txnName = getCombinedScopeView().getTransactionName();
    if (txnName != null) {
      tags.put("transaction", txnName);
    }
    return Collections.unmodifiableMap(tags);
  }

  @Override
  public @Nullable ISpan startSpanForMetric(@NotNull String op, @NotNull String description) {
    final @Nullable ISpan span = getSpan();
    if (span != null) {
      return span.startChild(op, description);
    }
    return null;
  }

  @Override
  public @Nullable LocalMetricsAggregator getLocalMetricsAggregator() {
    if (!options.isEnableSpanLocalMetricAggregation()) {
      return null;
    }
    final @Nullable ISpan span = getSpan();
    if (span != null) {
      return span.getLocalMetricsAggregator();
    }
    return null;
  }

  private static void validateOptions(final @NotNull SentryOptions options) {
    Objects.requireNonNull(options, "SentryOptions is required.");
    if (options.getDsn() == null || options.getDsn().isEmpty()) {
      throw new IllegalArgumentException(
          "Hub requires a DSN to be instantiated. Considering using the NoOpHub if no DSN is available.");
    }
  }
}
