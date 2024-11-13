package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.transport.RateLimiter;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
/**
 * @deprecated use {@link NoOpScopes} instead.
 */
public final class NoOpHub implements IHub {

  private static final NoOpHub instance = new NoOpHub();

  private final @NotNull SentryOptions emptyOptions = SentryOptions.empty();

  private NoOpHub() {}

  @Deprecated
  public static NoOpHub getInstance() {
    return instance;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureMessage(@NotNull String message, @NotNull SentryLevel level) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureMessage(
      @NotNull String message, @NotNull SentryLevel level, @NotNull ScopeCallback callback) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureEnvelope(@NotNull SentryEnvelope envelope, @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureException(@NotNull Throwable throwable, @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureException(
      @NotNull Throwable throwable, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public void captureUserFeedback(@NotNull UserFeedback userFeedback) {}

  @Override
  public void startSession() {}

  @Override
  public void endSession() {}

  @Override
  public void close() {}

  @Override
  public void close(final boolean isRestarting) {}

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {}

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {}

  @Override
  public void setLevel(@Nullable SentryLevel level) {}

  @Override
  public void setTransaction(@Nullable String transaction) {}

  @Override
  public void setUser(@Nullable User user) {}

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {}

  @Override
  public void clearBreadcrumbs() {}

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {}

  @Override
  public void removeTag(@NotNull String key) {}

  @Override
  public void setExtra(@NotNull String key, @NotNull String value) {}

  @Override
  public void removeExtra(@NotNull String key) {}

  @Override
  public @NotNull SentryId getLastEventId() {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull ISentryLifecycleToken pushScope() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  public @NotNull ISentryLifecycleToken pushIsolationScope() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  /**
   * @deprecated please call {@link ISentryLifecycleToken#close()} on the token returned by {@link
   *     IScopes#pushScope()} or {@link IScopes#pushIsolationScope()} instead.
   */
  @Override
  @Deprecated
  public void popScope() {}

  @Override
  public void withScope(@NotNull ScopeCallback callback) {
    callback.run(NoOpScope.getInstance());
  }

  @Override
  public void withIsolationScope(@NotNull ScopeCallback callback) {
    callback.run(NoOpScope.getInstance());
  }

  @Override
  public void configureScope(@Nullable ScopeType scopeType, @NotNull ScopeCallback callback) {}

  @Override
  public void bindClient(@NotNull ISentryClient client) {}

  @Override
  public boolean isHealthy() {
    return true;
  }

  @Override
  public void flush(long timeoutMillis) {}

  /**
   * @deprecated please use {@link IScopes#forkedScopes(String)} or {@link
   *     IScopes#forkedCurrentScope(String)} instead.
   */
  @Deprecated
  @Override
  public @NotNull IHub clone() {
    return instance;
  }

  @Override
  public @NotNull IScopes forkedScopes(@NotNull String creator) {
    return NoOpScopes.getInstance();
  }

  @Override
  public @NotNull IScopes forkedCurrentScope(@NotNull String creator) {
    return NoOpScopes.getInstance();
  }

  @Override
  public @NotNull ISentryLifecycleToken makeCurrent() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getScope() {
    return NoOpScope.getInstance();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getIsolationScope() {
    return NoOpScope.getInstance();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getGlobalScope() {
    return NoOpScope.getInstance();
  }

  @Override
  public @Nullable IScopes getParentScopes() {
    return null;
  }

  @Override
  public boolean isAncestorOf(@Nullable IScopes otherScopes) {
    return false;
  }

  @Override
  public @NotNull IScopes forkedRootScopes(final @NotNull String creator) {
    return NoOpScopes.getInstance();
  }

  @Override
  public @NotNull SentryId captureTransaction(
      final @NotNull SentryTransaction transaction,
      final @Nullable TraceContext traceContext,
      final @Nullable Hint hint,
      final @Nullable ProfilingTraceData profilingTraceData) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureProfileChunk(final @NotNull ProfileChunk profileChunk) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContext,
      @NotNull TransactionOptions transactionOptions) {
    return NoOpTransaction.getInstance();
  }

  @Override
  public void setSpanContext(
      final @NotNull Throwable throwable,
      final @NotNull ISpan spanContext,
      final @NotNull String transactionName) {}

  @Override
  public @Nullable ISpan getSpan() {
    return null;
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {}

  @Override
  public @Nullable ITransaction getTransaction() {
    return null;
  }

  @Override
  public @NotNull SentryOptions getOptions() {
    return emptyOptions;
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return null;
  }

  @Override
  public void reportFullyDisplayed() {}

  @Override
  public @Nullable TransactionContext continueTrace(
      final @Nullable String sentryTrace, final @Nullable List<String> baggageHeaders) {
    return null;
  }

  @Override
  public @Nullable SentryTraceHeader getTraceparent() {
    return null;
  }

  @Override
  public @Nullable BaggageHeader getBaggage() {
    return null;
  }

  @Override
  @ApiStatus.Experimental
  public @NotNull SentryId captureCheckIn(final @NotNull CheckIn checkIn) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureReplay(@NotNull SentryReplayEvent replay, @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return null;
  }

  @Override
  public boolean isNoOp() {
    return true;
  }
}
