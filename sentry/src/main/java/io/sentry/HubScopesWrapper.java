package io.sentry;

import io.sentry.logger.ILoggerApi;
import io.sentry.metrics.IMetricsApi;
import io.sentry.protocol.Feedback;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.transport.RateLimiter;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
@Deprecated
public final class HubScopesWrapper implements IHub {

  private final @NotNull IScopes scopes;

  public HubScopesWrapper(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  public @NotNull IScopes getScopes() {
    return scopes;
  }

  @Override
  public boolean isEnabled() {
    return scopes.isEnabled();
  }

  @Override
  public @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Hint hint) {
    return scopes.captureEvent(event, hint);
  }

  @Override
  public @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return scopes.captureEvent(event, hint, callback);
  }

  @Override
  public @NotNull SentryId captureMessage(@NotNull String message, @NotNull SentryLevel level) {
    return scopes.captureMessage(message, level);
  }

  @Override
  public @NotNull SentryId captureMessage(
      @NotNull String message, @NotNull SentryLevel level, @NotNull ScopeCallback callback) {
    return scopes.captureMessage(message, level, callback);
  }

  @Override
  public @NotNull SentryId captureFeedback(
      @NotNull Feedback feedback, @Nullable Hint hint, @Nullable ScopeCallback callback) {
    return scopes.captureFeedback(feedback, hint, callback);
  }

  @Override
  public @NotNull SentryId captureEnvelope(@NotNull SentryEnvelope envelope, @Nullable Hint hint) {
    return scopes.captureEnvelope(envelope, hint);
  }

  @Override
  public @NotNull SentryId captureException(@NotNull Throwable throwable, @Nullable Hint hint) {
    return scopes.captureException(throwable, hint);
  }

  @Override
  public @NotNull SentryId captureException(
      @NotNull Throwable throwable, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return scopes.captureException(throwable, hint, callback);
  }

  @Override
  public void captureUserFeedback(@NotNull UserFeedback userFeedback) {
    scopes.captureUserFeedback(userFeedback);
  }

  @Override
  public void startSession() {
    scopes.startSession();
  }

  @Override
  public void endSession() {
    scopes.endSession();
  }

  @Override
  public void close() {
    scopes.close();
  }

  @Override
  public void close(boolean isRestarting) {
    scopes.close(isRestarting);
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {
    scopes.addBreadcrumb(breadcrumb, hint);
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {
    scopes.addBreadcrumb(breadcrumb);
  }

  @Override
  public void setLevel(@Nullable SentryLevel level) {
    scopes.setLevel(level);
  }

  @Override
  public void setTransaction(@Nullable String transaction) {
    scopes.setTransaction(transaction);
  }

  @Override
  public void setUser(@Nullable User user) {
    scopes.setUser(user);
  }

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {
    scopes.setFingerprint(fingerprint);
  }

  @Override
  public void clearBreadcrumbs() {
    scopes.clearBreadcrumbs();
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {
    scopes.setTag(key, value);
  }

  @Override
  public void removeTag(@Nullable String key) {
    scopes.removeTag(key);
  }

  @Override
  public void setExtra(@Nullable String key, @Nullable String value) {
    scopes.setExtra(key, value);
  }

  @Override
  public void removeExtra(@Nullable String key) {
    scopes.removeExtra(key);
  }

  @Override
  public @NotNull SentryId getLastEventId() {
    return scopes.getLastEventId();
  }

  @Override
  public @NotNull ISentryLifecycleToken pushScope() {
    return scopes.pushScope();
  }

  @Override
  public @NotNull ISentryLifecycleToken pushIsolationScope() {
    return scopes.pushIsolationScope();
  }

  /**
   * @deprecated please call {@link ISentryLifecycleToken#close()} on the token returned by {@link
   *     IScopes#pushScope()} or {@link IScopes#pushIsolationScope()} instead.
   */
  @Override
  @Deprecated
  public void popScope() {
    scopes.popScope();
  }

  @Override
  public void withScope(@NotNull ScopeCallback callback) {
    scopes.withScope(callback);
  }

  @Override
  public void withIsolationScope(@NotNull ScopeCallback callback) {
    scopes.withIsolationScope(callback);
  }

  @Override
  public void configureScope(@Nullable ScopeType scopeType, @NotNull ScopeCallback callback) {
    scopes.configureScope(scopeType, callback);
  }

  @Override
  public void bindClient(@NotNull ISentryClient client) {
    scopes.bindClient(client);
  }

  @Override
  public boolean isHealthy() {
    return scopes.isHealthy();
  }

  @Override
  public void flush(long timeoutMillis) {
    scopes.flush(timeoutMillis);
  }

  /**
   * @deprecated please use {@link IScopes#forkedScopes(String)} or {@link
   *     IScopes#forkedCurrentScope(String)} instead.
   */
  @Override
  @Deprecated
  public @NotNull IHub clone() {
    return scopes.clone();
  }

  @Override
  public @NotNull IScopes forkedScopes(@NotNull String creator) {
    return scopes.forkedScopes(creator);
  }

  @Override
  public @NotNull IScopes forkedCurrentScope(@NotNull String creator) {
    return scopes.forkedCurrentScope(creator);
  }

  @Override
  public @NotNull IScopes forkedRootScopes(final @NotNull String creator) {
    return Sentry.forkedRootScopes(creator);
  }

  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    return scopes.makeCurrent();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getScope() {
    return scopes.getScope();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getIsolationScope() {
    return scopes.getIsolationScope();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getGlobalScope() {
    return Sentry.getGlobalScope();
  }

  @Override
  public @Nullable IScopes getParentScopes() {
    return scopes.getParentScopes();
  }

  @Override
  public boolean isAncestorOf(final @Nullable IScopes otherScopes) {
    return scopes.isAncestorOf(otherScopes);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable Hint hint,
      @Nullable ProfilingTraceData profilingTraceData) {
    return scopes.captureTransaction(transaction, traceContext, hint, profilingTraceData);
  }

  @Override
  public @NotNull SentryId captureProfileChunk(@NotNull ProfileChunk profileChunk) {
    return scopes.captureProfileChunk(profileChunk);
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContext,
      @NotNull TransactionOptions transactionOptions) {
    return scopes.startTransaction(transactionContext, transactionOptions);
  }

  @Override
  public void startProfiler() {
    scopes.startProfiler();
  }

  @Override
  public void stopProfiler() {
    scopes.stopProfiler();
  }

  @ApiStatus.Internal
  @Override
  public void setSpanContext(
      @NotNull Throwable throwable, @NotNull ISpan span, @NotNull String transactionName) {
    scopes.setSpanContext(throwable, span, transactionName);
  }

  @Override
  public @Nullable ISpan getSpan() {
    return scopes.getSpan();
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {
    scopes.setActiveSpan(span);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable ITransaction getTransaction() {
    return scopes.getTransaction();
  }

  @Override
  public @NotNull SentryOptions getOptions() {
    return scopes.getOptions();
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return scopes.isCrashedLastRun();
  }

  @Override
  public void reportFullyDisplayed() {
    scopes.reportFullyDisplayed();
  }

  @Override
  public @Nullable TransactionContext continueTrace(
      @Nullable String sentryTrace, @Nullable List<String> baggageHeaders) {
    return scopes.continueTrace(sentryTrace, baggageHeaders);
  }

  @Override
  public @Nullable SentryTraceHeader getTraceparent() {
    return scopes.getTraceparent();
  }

  @Override
  public @Nullable BaggageHeader getBaggage() {
    return scopes.getBaggage();
  }

  @Override
  public @NotNull SentryId captureCheckIn(@NotNull CheckIn checkIn) {
    return scopes.captureCheckIn(checkIn);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return scopes.getRateLimiter();
  }

  @Override
  public @NotNull SentryId captureReplay(@NotNull SentryReplayEvent replay, @Nullable Hint hint) {
    return scopes.captureReplay(replay, hint);
  }

  @Override
  public @NotNull ILoggerApi logger() {
    return scopes.logger();
  }

  @Override
  public @NotNull IMetricsApi metrics() {
    return scopes.metrics();
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {
    scopes.addFeatureFlag(flag, result);
  }
}
