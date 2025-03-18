package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.transport.RateLimiter;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link ScopesAdapter} instead
 */
@Deprecated
public final class HubAdapter implements IHub {

  private static final HubAdapter INSTANCE = new HubAdapter();

  private HubAdapter() {}

  public static HubAdapter getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isEnabled() {
    return Sentry.isEnabled();
  }

  @Override
  public @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Hint hint) {
    return Sentry.captureEvent(event, hint);
  }

  @Override
  public @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return Sentry.captureEvent(event, hint, callback);
  }

  @Override
  public @NotNull SentryId captureMessage(@NotNull String message, @NotNull SentryLevel level) {
    return Sentry.captureMessage(message, level);
  }

  @Override
  public @NotNull SentryId captureMessage(
      @NotNull String message, @NotNull SentryLevel level, @NotNull ScopeCallback callback) {
    return Sentry.captureMessage(message, level, callback);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull SentryId captureEnvelope(@NotNull SentryEnvelope envelope, @Nullable Hint hint) {
    return Sentry.getCurrentScopes().captureEnvelope(envelope, hint);
  }

  @Override
  public @NotNull SentryId captureException(@NotNull Throwable throwable, @Nullable Hint hint) {
    return Sentry.captureException(throwable, hint);
  }

  @Override
  public @NotNull SentryId captureException(
      @NotNull Throwable throwable, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return Sentry.captureException(throwable, hint, callback);
  }

  @Override
  public void captureUserFeedback(@NotNull UserFeedback userFeedback) {
    Sentry.captureUserFeedback(userFeedback);
  }

  @Override
  public void startSession() {
    Sentry.startSession();
  }

  @Override
  public void endSession() {
    Sentry.endSession();
  }

  @Override
  public void close(final boolean isRestarting) {
    Sentry.close();
  }

  @Override
  public void close() {
    Sentry.close();
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {
    Sentry.addBreadcrumb(breadcrumb, hint);
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, new Hint());
  }

  @Override
  public void setLevel(@Nullable SentryLevel level) {
    Sentry.setLevel(level);
  }

  @Override
  public void setTransaction(@Nullable String transaction) {
    Sentry.setTransaction(transaction);
  }

  @Override
  public void setUser(@Nullable User user) {
    Sentry.setUser(user);
  }

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {
    Sentry.setFingerprint(fingerprint);
  }

  @Override
  public void clearBreadcrumbs() {
    Sentry.clearBreadcrumbs();
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {
    Sentry.setTag(key, value);
  }

  @Override
  public void removeTag(@Nullable String key) {
    Sentry.removeTag(key);
  }

  @Override
  public void setExtra(@Nullable String key, @Nullable String value) {
    Sentry.setExtra(key, value);
  }

  @Override
  public void removeExtra(@Nullable String key) {
    Sentry.removeExtra(key);
  }

  @Override
  public @NotNull SentryId getLastEventId() {
    return Sentry.getLastEventId();
  }

  @Override
  public @NotNull ISentryLifecycleToken pushScope() {
    return Sentry.pushScope();
  }

  @Override
  public @NotNull ISentryLifecycleToken pushIsolationScope() {
    return Sentry.pushIsolationScope();
  }

  /**
   * @deprecated please call {@link ISentryLifecycleToken#close()} on the token returned by {@link
   *     ScopesAdapter#pushScope()} or {@link ScopesAdapter#pushIsolationScope()} instead.
   */
  @Override
  @Deprecated
  public void popScope() {
    Sentry.popScope();
  }

  @Override
  public void withScope(@NotNull ScopeCallback callback) {
    Sentry.withScope(callback);
  }

  @Override
  public void withIsolationScope(@NotNull ScopeCallback callback) {
    Sentry.withIsolationScope(callback);
  }

  @Override
  public void configureScope(@Nullable ScopeType scopeType, @NotNull ScopeCallback callback) {
    Sentry.configureScope(scopeType, callback);
  }

  @Override
  public void bindClient(@NotNull ISentryClient client) {
    Sentry.bindClient(client);
  }

  @Override
  public boolean isHealthy() {
    return Sentry.isHealthy();
  }

  @Override
  public void flush(long timeoutMillis) {
    Sentry.flush(timeoutMillis);
  }

  /**
   * @deprecated please use {@link IScopes#forkedScopes(String)} or {@link
   *     IScopes#forkedCurrentScope(String)} instead.
   */
  @Deprecated
  @Override
  public @NotNull IHub clone() {
    return Sentry.getCurrentScopes().clone();
  }

  @Override
  public @NotNull IScopes forkedScopes(@NotNull String creator) {
    return Sentry.forkedScopes(creator);
  }

  @Override
  public @NotNull IScopes forkedCurrentScope(@NotNull String creator) {
    return Sentry.forkedCurrentScope(creator);
  }

  @Override
  public @NotNull IScopes forkedRootScopes(final @NotNull String creator) {
    return Sentry.forkedRootScopes(creator);
  }

  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getScope() {
    return Sentry.getCurrentScopes().getScope();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getIsolationScope() {
    return Sentry.getCurrentScopes().getIsolationScope();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getGlobalScope() {
    return Sentry.getGlobalScope();
  }

  @Override
  public @Nullable IScopes getParentScopes() {
    return Sentry.getCurrentScopes().getParentScopes();
  }

  @Override
  public boolean isAncestorOf(final @Nullable IScopes otherScopes) {
    return Sentry.getCurrentScopes().isAncestorOf(otherScopes);
  }

  @Override
  public @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable Hint hint,
      @Nullable ProfilingTraceData profilingTraceData) {
    return Sentry.getCurrentScopes()
        .captureTransaction(transaction, traceContext, hint, profilingTraceData);
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContext,
      @NotNull TransactionOptions transactionOptions) {
    return Sentry.startTransaction(transactionContext, transactionOptions);
  }

  @Override
  public void startProfileSession() {
    Sentry.startProfileSession();
  }

  @Override
  public void stopProfileSession() {
    Sentry.stopProfileSession();
  }

  @Override
  public @NotNull SentryId captureProfileChunk(
      final @NotNull ProfileChunk profilingContinuousData) {
    return Sentry.getCurrentScopes().captureProfileChunk(profilingContinuousData);
  }

  @Override
  public void setSpanContext(
      final @NotNull Throwable throwable,
      final @NotNull ISpan span,
      final @NotNull String transactionName) {
    Sentry.getCurrentScopes().setSpanContext(throwable, span, transactionName);
  }

  @Override
  public @Nullable ISpan getSpan() {
    return Sentry.getCurrentScopes().getSpan();
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {
    Sentry.getCurrentScopes().setActiveSpan(span);
  }

  @Override
  @ApiStatus.Internal
  public @Nullable ITransaction getTransaction() {
    return Sentry.getCurrentScopes().getTransaction();
  }

  @Override
  public @NotNull SentryOptions getOptions() {
    return Sentry.getCurrentScopes().getOptions();
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return Sentry.isCrashedLastRun();
  }

  @Override
  public void reportFullyDisplayed() {
    Sentry.reportFullyDisplayed();
  }

  @Override
  public @Nullable TransactionContext continueTrace(
      final @Nullable String sentryTrace, final @Nullable List<String> baggageHeaders) {
    return Sentry.continueTrace(sentryTrace, baggageHeaders);
  }

  @Override
  public @Nullable SentryTraceHeader getTraceparent() {
    return Sentry.getTraceparent();
  }

  @Override
  public @Nullable BaggageHeader getBaggage() {
    return Sentry.getBaggage();
  }

  @Override
  @ApiStatus.Experimental
  public @NotNull SentryId captureCheckIn(final @NotNull CheckIn checkIn) {
    return Sentry.captureCheckIn(checkIn);
  }

  @Override
  public @NotNull SentryId captureReplay(
      final @NotNull SentryReplayEvent replay, final @Nullable Hint hint) {
    return Sentry.getCurrentScopes().captureReplay(replay, hint);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return Sentry.getCurrentScopes().getRateLimiter();
  }
}
