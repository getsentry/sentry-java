package io.sentry;

import io.sentry.clientreport.DiscardReason;
import io.sentry.hints.SessionEndHint;
import io.sentry.hints.SessionStartHint;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.transport.RateLimiter;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import io.sentry.util.SpanUtils;
import io.sentry.util.TracingUtils;
import java.io.Closeable;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Scopes implements IScopes {

  private final @NotNull IScope scope;
  private final @NotNull IScope isolationScope;
  private final @NotNull IScope globalScope;

  private final @Nullable Scopes parentScopes;

  private final @NotNull String creator;
  private final @NotNull TransactionPerformanceCollector transactionPerformanceCollector;

  private final @NotNull CombinedScopeView combinedScope;

  public Scopes(
      final @NotNull IScope scope,
      final @NotNull IScope isolationScope,
      final @NotNull IScope globalScope,
      final @NotNull String creator) {
    this(scope, isolationScope, globalScope, null, creator);
  }

  private Scopes(
      final @NotNull IScope scope,
      final @NotNull IScope isolationScope,
      final @NotNull IScope globalScope,
      final @Nullable Scopes parentScopes,
      final @NotNull String creator) {
    this.combinedScope = new CombinedScopeView(globalScope, isolationScope, scope);
    this.scope = scope;
    this.isolationScope = isolationScope;
    this.globalScope = globalScope;
    this.parentScopes = parentScopes;
    this.creator = creator;

    final @NotNull SentryOptions options = getOptions();
    validateOptions(options);
    this.transactionPerformanceCollector = options.getTransactionPerformanceCollector();
  }

  public @NotNull String getCreator() {
    return creator;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getScope() {
    return scope;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getIsolationScope() {
    return isolationScope;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getGlobalScope() {
    return globalScope;
  }

  @Override
  @ApiStatus.Internal
  public @Nullable IScopes getParentScopes() {
    return parentScopes;
  }

  @Override
  @ApiStatus.Internal
  public boolean isAncestorOf(final @Nullable IScopes otherScopes) {
    if (otherScopes == null) {
      return false;
    }

    if (this == otherScopes) {
      return true;
    }

    if (otherScopes.getParentScopes() != null) {
      return isAncestorOf(otherScopes.getParentScopes());
    }

    return false;
  }

  @Override
  public @NotNull IScopes forkedScopes(final @NotNull String creator) {
    return new Scopes(scope.clone(), isolationScope.clone(), globalScope, this, creator);
  }

  @Override
  public @NotNull IScopes forkedCurrentScope(final @NotNull String creator) {
    return new Scopes(scope.clone(), isolationScope, globalScope, this, creator);
  }

  @Override
  public @NotNull IScopes forkedRootScopes(final @NotNull String creator) {
    return Sentry.forkedRootScopes(creator);
  }

  @Override
  public boolean isEnabled() {
    return getClient().isEnabled();
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
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING, "Instance is disabled and this 'captureEvent' call is a no-op.");
    } else if (event == null) {
      getOptions().getLogger().log(SentryLevel.WARNING, "captureEvent called with null parameter.");
    } else {
      try {
        assignTraceContext(event);
        final IScope localScope = buildLocalScope(getCombinedScopeView(), scopeCallback);

        sentryId = getClient().captureEvent(event, localScope, hint);
        updateLastEventId(sentryId);
      } catch (Throwable e) {
        getOptions()
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
    getCombinedScopeView().assignTraceContext(event);
  }

  private IScope buildLocalScope(
      final @NotNull IScope parentScope, final @Nullable ScopeCallback callback) {
    if (callback != null) {
      try {
        final IScope localScope = parentScope.clone();
        callback.run(localScope);
        return localScope;
      } catch (Throwable t) {
        getOptions()
            .getLogger()
            .log(SentryLevel.ERROR, "Error in the 'ScopeCallback' callback.", t);
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
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureMessage' call is a no-op.");
    } else if (message == null) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "captureMessage called with null parameter.");
    } else {
      try {
        final IScope localScope = buildLocalScope(getCombinedScopeView(), scopeCallback);

        sentryId = getClient().captureMessage(message, level, localScope);
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(SentryLevel.ERROR, "Error while capturing message: " + message, e);
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
      getOptions()
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
        getOptions().getLogger().log(SentryLevel.ERROR, "Error while capturing envelope.", e);
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
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureException' call is a no-op.");
    } else if (throwable == null) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "captureException called with null parameter.");
    } else {
      try {
        final SentryEvent event = new SentryEvent(throwable);
        assignTraceContext(event);

        final IScope localScope = buildLocalScope(getCombinedScopeView(), scopeCallback);

        sentryId = getClient().captureEvent(event, localScope, hint);
      } catch (Throwable e) {
        getOptions()
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
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureUserFeedback' call is a no-op.");
    } else {
      try {
        getClient().captureUserFeedback(userFeedback);
      } catch (Throwable e) {
        getOptions()
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
      getOptions()
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
        getOptions().getLogger().log(SentryLevel.WARNING, "Session could not be started.");
      }
    }
  }

  @Override
  public void endSession() {
    if (!isEnabled()) {
      getOptions()
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
    return combinedScope;
  }

  @Override
  public void close() {
    close(false);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void close(final boolean isRestarting) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'close' call is a no-op.");
    } else {
      try {
        for (Integration integration : getOptions().getIntegrations()) {
          if (integration instanceof Closeable) {
            try {
              ((Closeable) integration).close();
            } catch (Throwable e) {
              getOptions()
                  .getLogger()
                  .log(SentryLevel.WARNING, "Failed to close the integration {}.", integration, e);
            }
          }
        }

        configureScope(scope -> scope.clear());
        configureScope(ScopeType.ISOLATION, scope -> scope.clear());
        getOptions().getBackpressureMonitor().close();
        getOptions().getTransactionProfiler().close();
        getOptions().getTransactionPerformanceCollector().close();
        final @NotNull ISentryExecutorService executorService = getOptions().getExecutorService();
        if (isRestarting) {
          executorService.submit(
              () -> executorService.close(getOptions().getShutdownTimeoutMillis()));
        } else {
          executorService.close(getOptions().getShutdownTimeoutMillis());
        }

        // TODO: should we end session before closing client?
        configureScope(ScopeType.CURRENT, scope -> scope.getClient().close(isRestarting));
        configureScope(ScopeType.ISOLATION, scope -> scope.getClient().close(isRestarting));
        configureScope(ScopeType.GLOBAL, scope -> scope.getClient().close(isRestarting));
      } catch (Throwable e) {
        getOptions().getLogger().log(SentryLevel.ERROR, "Error while closing the Scopes.", e);
      }
    }
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb, final @Nullable Hint hint) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'addBreadcrumb' call is a no-op.");
    } else if (breadcrumb == null) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "addBreadcrumb called with null parameter.");
    } else {
      getCombinedScopeView().addBreadcrumb(breadcrumb, hint);
    }
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, new Hint());
  }

  @Override
  public void setLevel(final @Nullable SentryLevel level) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setLevel' call is a no-op.");
    } else {
      getCombinedScopeView().setLevel(level);
    }
  }

  @Override
  public void setTransaction(final @Nullable String transaction) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'setTransaction' call is a no-op.");
    } else if (transaction != null) {
      getCombinedScopeView().setTransaction(transaction);
    } else {
      getOptions().getLogger().log(SentryLevel.WARNING, "Transaction cannot be null");
    }
  }

  @Override
  public void setUser(final @Nullable User user) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setUser' call is a no-op.");
    } else {
      getCombinedScopeView().setUser(user);
    }
  }

  @Override
  public void setFingerprint(final @NotNull List<String> fingerprint) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'setFingerprint' call is a no-op.");
    } else if (fingerprint == null) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "setFingerprint called with null parameter.");
    } else {
      getCombinedScopeView().setFingerprint(fingerprint);
    }
  }

  @Override
  public void clearBreadcrumbs() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'clearBreadcrumbs' call is a no-op.");
    } else {
      getCombinedScopeView().clearBreadcrumbs();
    }
  }

  @Override
  public void setTag(final @NotNull String key, final @NotNull String value) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setTag' call is a no-op.");
    } else if (key == null || value == null) {
      getOptions().getLogger().log(SentryLevel.WARNING, "setTag called with null parameter.");
    } else {
      getCombinedScopeView().setTag(key, value);
    }
  }

  @Override
  public void removeTag(final @NotNull String key) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'removeTag' call is a no-op.");
    } else if (key == null) {
      getOptions().getLogger().log(SentryLevel.WARNING, "removeTag called with null parameter.");
    } else {
      getCombinedScopeView().removeTag(key);
    }
  }

  @Override
  public void setExtra(final @NotNull String key, final @NotNull String value) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setExtra' call is a no-op.");
    } else if (key == null || value == null) {
      getOptions().getLogger().log(SentryLevel.WARNING, "setExtra called with null parameter.");
    } else {
      getCombinedScopeView().setExtra(key, value);
    }
  }

  @Override
  public void removeExtra(final @NotNull String key) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'removeExtra' call is a no-op.");
    } else if (key == null) {
      getOptions().getLogger().log(SentryLevel.WARNING, "removeExtra called with null parameter.");
    } else {
      getCombinedScopeView().removeExtra(key);
    }
  }

  private void updateLastEventId(final @NotNull SentryId lastEventId) {
    getCombinedScopeView().setLastEventId(lastEventId);
  }

  @Override
  public @NotNull SentryId getLastEventId() {
    return getCombinedScopeView().getLastEventId();
  }

  @Override
  public ISentryLifecycleToken pushScope() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'pushScope' call is a no-op.");
      return NoOpScopesLifecycleToken.getInstance();
    } else {
      final @NotNull IScopes scopes = this.forkedCurrentScope("pushScope");
      return scopes.makeCurrent();
    }
  }

  @Override
  public ISentryLifecycleToken pushIsolationScope() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'pushIsolationScope' call is a no-op.");
      return NoOpScopesLifecycleToken.getInstance();
    } else {
      final @NotNull IScopes scopes = this.forkedScopes("pushIsolationScope");
      return scopes.makeCurrent();
    }
  }

  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    return Sentry.setCurrentScopes(this);
  }

  /**
   * @deprecated please call {@link ISentryLifecycleToken#close()} on the token returned by {@link
   *     IScopes#pushScope()} or {@link IScopes#pushIsolationScope()} instead.
   */
  @Override
  @Deprecated
  public void popScope() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'popScope' call is a no-op.");
    } else {
      final @Nullable Scopes parent = parentScopes;
      if (parent != null) {
        parent.makeCurrent();
      }
    }
  }

  @Override
  public void withScope(final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      try {
        callback.run(NoOpScope.getInstance());
      } catch (Throwable e) {
        getOptions().getLogger().log(SentryLevel.ERROR, "Error in the 'withScope' callback.", e);
      }

    } else {
      final @NotNull IScopes forkedScopes = forkedCurrentScope("withScope");
      try (final @NotNull ISentryLifecycleToken ignored = forkedScopes.makeCurrent()) {
        callback.run(forkedScopes.getScope());
      } catch (Throwable e) {
        getOptions().getLogger().log(SentryLevel.ERROR, "Error in the 'withScope' callback.", e);
      }
    }
  }

  @Override
  public void withIsolationScope(final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      try {
        callback.run(NoOpScope.getInstance());
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(SentryLevel.ERROR, "Error in the 'withIsolationScope' callback.", e);
      }

    } else {
      final @NotNull IScopes forkedScopes = forkedScopes("withIsolationScope");
      try (final @NotNull ISentryLifecycleToken ignored = forkedScopes.makeCurrent()) {
        callback.run(forkedScopes.getIsolationScope());
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(SentryLevel.ERROR, "Error in the 'withIsolationScope' callback.", e);
      }
    }
  }

  @Override
  public void configureScope(
      final @Nullable ScopeType scopeType, final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'configureScope' call is a no-op.");
    } else {
      try {
        callback.run(combinedScope.getSpecificScope(scopeType));
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(SentryLevel.ERROR, "Error in the 'configureScope' callback.", e);
      }
    }
  }

  @Override
  public void bindClient(final @NotNull ISentryClient client) {
    if (client != null) {
      getOptions().getLogger().log(SentryLevel.DEBUG, "New client bound to scope.");
      getCombinedScopeView().bindClient(client);
    } else {
      getOptions().getLogger().log(SentryLevel.DEBUG, "NoOp client bound to scope.");
      getCombinedScopeView().bindClient(NoOpSentryClient.getInstance());
    }
  }

  @Override
  public boolean isHealthy() {
    return getClient().isHealthy();
  }

  @Override
  public void flush(long timeoutMillis) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'flush' call is a no-op.");
    } else {
      try {
        getClient().flush(timeoutMillis);
      } catch (Throwable e) {
        getOptions().getLogger().log(SentryLevel.ERROR, "Error in the 'client.flush'.", e);
      }
    }
  }

  /**
   * @deprecated please use {@link IScopes#forkedScopes(String)} or {@link
   *     IScopes#forkedCurrentScope(String)} instead.
   */
  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public @NotNull IHub clone() {
    if (!isEnabled()) {
      getOptions().getLogger().log(SentryLevel.WARNING, "Disabled Scopes cloned.");
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
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureTransaction' call is a no-op.");
    } else {
      if (!transaction.isFinished()) {
        getOptions()
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Transaction: %s is not finished and this 'captureTransaction' call is a no-op.",
                transaction.getEventId());
      } else {
        if (!Boolean.TRUE.equals(transaction.isSampled())) {
          getOptions()
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Transaction %s was dropped due to sampling decision.",
                  transaction.getEventId());
          if (getOptions().getBackpressureMonitor().getDownsampleFactor() > 0) {
            getOptions()
                .getClientReportRecorder()
                .recordLostEvent(DiscardReason.BACKPRESSURE, DataCategory.Transaction);
            getOptions()
                .getClientReportRecorder()
                .recordLostEvent(
                    DiscardReason.BACKPRESSURE,
                    DataCategory.Span,
                    transaction.getSpans().size() + 1);
          } else {
            getOptions()
                .getClientReportRecorder()
                .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Transaction);
            getOptions()
                .getClientReportRecorder()
                .recordLostEvent(
                    DiscardReason.SAMPLE_RATE,
                    DataCategory.Span,
                    transaction.getSpans().size() + 1);
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
            getOptions()
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
    transactionContext.setOrigin(transactionOptions.getOrigin());

    ITransaction transaction;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'startTransaction' returns a no-op.");
      transaction = NoOpTransaction.getInstance();
    } else if (SpanUtils.isIgnored(
        getOptions().getIgnoredSpanOrigins(), transactionContext.getOrigin())) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Returning no-op for span origin %s as the SDK has been configured to ignore it",
              transactionContext.getOrigin());
      transaction = NoOpTransaction.getInstance();

    } else if (!getOptions().getInstrumenter().equals(transactionContext.getInstrumenter())) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Returning no-op for instrumenter %s as the SDK has been configured to use instrumenter %s",
              transactionContext.getInstrumenter(),
              getOptions().getInstrumenter());
      transaction = NoOpTransaction.getInstance();
    } else if (!getOptions().isTracingEnabled()) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.INFO, "Tracing is disabled and this 'startTransaction' returns a no-op.");
      transaction = NoOpTransaction.getInstance();
    } else {
      final Double sampleRand = getSampleRand(transactionContext);
      final SamplingContext samplingContext =
          new SamplingContext(
              transactionContext, transactionOptions.getCustomSamplingContext(), sampleRand);
      final @NotNull TracesSampler tracesSampler = getOptions().getInternalTracesSampler();
      @NotNull TracesSamplingDecision samplingDecision = tracesSampler.sample(samplingContext);
      transactionContext.setSamplingDecision(samplingDecision);

      final @Nullable ISpanFactory maybeSpanFactory = transactionOptions.getSpanFactory();
      final @NotNull ISpanFactory spanFactory =
          maybeSpanFactory == null ? getOptions().getSpanFactory() : maybeSpanFactory;

      transaction =
          spanFactory.createTransaction(
              transactionContext, this, transactionOptions, transactionPerformanceCollector);
      //          new SentryTracer(
      //              transactionContext, this, transactionOptions,
      // transactionPerformanceCollector);

      // The listener is called only if the transaction exists, as the transaction is needed to
      // stop it
      if (samplingDecision.getSampled() && samplingDecision.getProfileSampled()) {
        final ITransactionProfiler transactionProfiler = getOptions().getTransactionProfiler();
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
      transaction.makeCurrent();
    }
    return transaction;
  }

  private @NotNull Double getSampleRand(final @NotNull TransactionContext transactionContext) {
    final @Nullable Baggage baggage = transactionContext.getBaggage();
    if (baggage != null) {
      final @Nullable Double sampleRandFromBaggageMaybe = baggage.getSampleRandDouble();
      if (sampleRandFromBaggageMaybe != null) {
        return sampleRandFromBaggageMaybe;
      }
    }

    return getCombinedScopeView().getPropagationContext().getSampleRand();
  }

  @Override
  @ApiStatus.Internal
  public void setSpanContext(
      final @NotNull Throwable throwable,
      final @NotNull ISpan span,
      final @NotNull String transactionName) {
    getCombinedScopeView().setSpanContext(throwable, span, transactionName);
  }

  @Override
  public @Nullable ISpan getSpan() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'getSpan' call is a no-op.");
    } else {
      return getCombinedScopeView().getSpan();
    }
    return null;
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {
    getCombinedScopeView().setActiveSpan(span);
  }

  @Override
  @ApiStatus.Internal
  public @Nullable ITransaction getTransaction() {
    ITransaction span = null;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'getTransaction' call is a no-op.");
    } else {
      span = getCombinedScopeView().getTransaction();
    }
    return span;
  }

  @Override
  public @NotNull SentryOptions getOptions() {
    return combinedScope.getOptions();
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return SentryCrashLastRunState.getInstance()
        .isCrashedLastRun(
            getOptions().getCacheDirPath(), !getOptions().isEnableAutoSessionTracking());
  }

  @Override
  public void reportFullyDisplayed() {
    if (getOptions().isEnableTimeToFullDisplayTracing()) {
      getOptions().getFullyDisplayedReporter().reportFullyDrawn();
    }
  }

  @Override
  public @Nullable TransactionContext continueTrace(
      final @Nullable String sentryTrace, final @Nullable List<String> baggageHeaders) {
    @NotNull
    PropagationContext propagationContext =
        PropagationContext.fromHeaders(getOptions().getLogger(), sentryTrace, baggageHeaders);
    configureScope(
        (scope) -> {
          scope.withPropagationContext(
            oldPropagationContext -> {
              scope.setPropagationContext(propagationContext);
            });
        });
    if (getOptions().isTracingEnabled()) {
      return TransactionContext.fromPropagationContext(propagationContext);
    } else {
      return null;
    }
  }

  @Override
  public void setTrace(
    final @NotNull String traceId, final @NotNull String spanId, final @Nullable Double sampleRate, final @Nullable Double sampleRand) {
    @NotNull PropagationContext propagationContext = PropagationContext.fromId(traceId, spanId, sampleRate, sampleRand);
    configureScope(
        (scope) -> {
          scope.setPropagationContext(propagationContext);
        });
  }

  @Override
  public @Nullable SentryTraceHeader getTraceparent() {
    if (!isEnabled()) {
      getOptions()
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
      getOptions()
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
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureCheckIn' call is a no-op.");
    } else {
      try {
        sentryId = getClient().captureCheckIn(checkIn, getCombinedScopeView(), null);
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(SentryLevel.ERROR, "Error while capturing check-in for slug", e);
      }
    }
    updateLastEventId(sentryId);
    return sentryId;
  }

  @Override
  public @NotNull SentryId captureReplay(
      final @NotNull SentryReplayEvent replay, final @Nullable Hint hint) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureReplay' call is a no-op.");
    } else {
      try {
        sentryId = getClient().captureReplayEvent(replay, getCombinedScopeView(), hint);
      } catch (Throwable e) {
        getOptions().getLogger().log(SentryLevel.ERROR, "Error while capturing replay", e);
      }
    }
    return sentryId;
  }

  @ApiStatus.Internal
  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return getClient().getRateLimiter();
  }

  private static void validateOptions(final @NotNull SentryOptions options) {
    Objects.requireNonNull(options, "SentryOptions is required.");
    if (options.getDsn() == null || options.getDsn().isEmpty()) {
      throw new IllegalArgumentException(
          "Scopes requires a DSN to be instantiated. Considering using the NoOpScopes if no DSN is available.");
    }
  }
}
