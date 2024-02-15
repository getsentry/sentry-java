package io.sentry;

import io.sentry.metrics.MetricsApi;
import io.sentry.metrics.NoopMetricsAggregator;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.transport.RateLimiter;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpHub implements IHub {

  private static final NoOpHub instance = new NoOpHub();

  private final @NotNull SentryOptions emptyOptions = SentryOptions.empty();
  private final @NotNull MetricsApi metricsApi =
      new MetricsApi(NoopMetricsAggregator.getInstance());

  private NoOpHub() {}

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
  public void pushScope() {}

  @Override
  public void popScope() {}

  @Override
  public void withScope(@NotNull ScopeCallback callback) {
    callback.run(NoOpScope.getInstance());
  }

  @Override
  public void configureScope(@NotNull ScopeCallback callback) {}

  @Override
  public void bindClient(@NotNull ISentryClient client) {}

  @Override
  public boolean isHealthy() {
    return true;
  }

  @Override
  public void flush(long timeoutMillis) {}

  @Override
  public @NotNull IHub clone() {
    return instance;
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
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContext,
      @NotNull TransactionOptions transactionOptions) {
    return NoOpTransaction.getInstance();
  }

  @Override
  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public @NotNull SentryTraceHeader traceHeaders() {
    return new SentryTraceHeader(SentryId.EMPTY_ID, SpanId.EMPTY_ID, true);
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
  public @Nullable RateLimiter getRateLimiter() {
    return null;
  }

  @Override
  public @NotNull MetricsApi metrics() {
    return metricsApi;
  }
}
