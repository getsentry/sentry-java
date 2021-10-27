package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpHub implements IHub {

  private static final NoOpHub instance = new NoOpHub();

  private final @NotNull SentryOptions emptyOptions = SentryOptions.empty();

  private NoOpHub() {}

  public static NoOpHub getInstance() {
    return instance;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Object hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureMessage(@NotNull String message, @NotNull SentryLevel level) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureEnvelope(
      @NotNull SentryEnvelope envelope, @Nullable Object hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureException(@NotNull Throwable throwable, @Nullable Object hint) {
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
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Object hint) {}

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
  public void withScope(@NotNull ScopeCallback callback) {}

  @Override
  public void configureScope(@NotNull ScopeCallback callback) {}

  @Override
  public void bindClient(@NotNull ISentryClient client) {}

  @Override
  public void flush(long timeoutMillis) {}

  @Override
  public @NotNull IHub clone() {
    return instance;
  }

  @Override
  public @NotNull SentryId captureTransaction(
      final @NotNull SentryTransaction transaction,
      final @Nullable TraceState traceState,
      final @Nullable Object hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull ITransaction startTransaction(@NotNull TransactionContext transactionContexts) {
    return NoOpTransaction.getInstance();
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContexts,
      @Nullable CustomSamplingContext customSamplingContext,
      boolean bindToScope) {
    return NoOpTransaction.getInstance();
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContexts,
      @Nullable CustomSamplingContext customSamplingContext,
      boolean bindToScope,
      @Nullable Date startTimestamp) {
    return NoOpTransaction.getInstance();
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContexts,
      @Nullable CustomSamplingContext customSamplingContext,
      boolean bindToScope,
      @Nullable Date startTimestamp,
      boolean waitForChildren,
      @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    return NoOpTransaction.getInstance();
  }

  @Override
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
  public @NotNull SentryOptions getOptions() {
    return emptyOptions;
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return null;
  }
}
