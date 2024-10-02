package io.sentry;

import io.sentry.metrics.MetricsApi;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.transport.RateLimiter;
import java.util.List;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TracingHub implements IHub {

  private static final ThreadLocal<ITransaction> txn = new ThreadLocal<>();
  private final IHub hub;

  private synchronized <T> T wrap(final @NotNull Callable<T> runnable, final @NotNull String op) {
    ITransaction transaction = txn.get();
    ISpan span = null;

    if (transaction == null) {
      transaction = hub.startTransaction(op, op);
      transaction.setTag("origin", "tracing");
      txn.set(transaction);
    } else {
      span = transaction.startChild(op);
      span.setTag("origin", "tracing");
    }

    try {
      return runnable.call();
    } catch (Throwable e) {
      // ignored
      throw new RuntimeException("Failed to execute operation: " + op, e);
    } finally {
      if (span != null) {
        span.finish();
      }
      transaction.finish();
      txn.remove();
    }
  }

  public TracingHub(IHub hub) {
    this.hub = hub;
  }

  @Override
  public boolean isEnabled() {
    return wrap(() -> hub.isEnabled(), "isEnabled");
  }

  @Override
  public @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Hint hint) {
    return wrap(() -> hub.captureEvent(event, hint), "captureEvent");
  }

  @Override
  public @NotNull SentryId captureEvent(@NotNull SentryEvent event) {
    return wrap(() -> hub.captureEvent(event), "captureEvent");
  }

  @Override
  public @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, @NotNull ScopeCallback callback) {
    return wrap(() -> hub.captureEvent(event, callback), "captureEvent");
  }

  @Override
  public @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return wrap(() -> hub.captureEvent(event, hint, callback), "captureEvent");
  }

  @Override
  public @NotNull SentryId captureMessage(@NotNull String message) {
    return wrap(() -> hub.captureMessage(message), "captureMessage");
  }

  @Override
  public @NotNull SentryId captureMessage(@NotNull String message, @NotNull SentryLevel level) {
    return wrap(() -> hub.captureMessage(message, level), "captureMessage");
  }

  @Override
  public @NotNull SentryId captureMessage(
      @NotNull String message, @NotNull SentryLevel level, @NotNull ScopeCallback callback) {
    return wrap(() -> hub.captureMessage(message, level, callback), "captureMessage");
  }

  @Override
  public @NotNull SentryId captureMessage(
      @NotNull String message, @NotNull ScopeCallback callback) {
    return wrap(() -> hub.captureMessage(message, callback), "captureMessage");
  }

  @Override
  public @NotNull SentryId captureEnvelope(@NotNull SentryEnvelope envelope, @Nullable Hint hint) {
    return wrap(() -> hub.captureEnvelope(envelope, hint), "captureEnvelope");
  }

  @Override
  public @NotNull SentryId captureEnvelope(@NotNull SentryEnvelope envelope) {
    return wrap(() -> hub.captureEnvelope(envelope), "captureEnvelope");
  }

  @Override
  public @NotNull SentryId captureException(@NotNull Throwable throwable, @Nullable Hint hint) {
    return wrap(() -> hub.captureException(throwable, hint), "captureException");
  }

  @Override
  public @NotNull SentryId captureException(@NotNull Throwable throwable) {
    return wrap(() -> hub.captureException(throwable), "captureException");
  }

  @Override
  public @NotNull SentryId captureException(
      @NotNull Throwable throwable, @NotNull ScopeCallback callback) {
    return wrap(() -> hub.captureException(throwable, callback), "captureException");
  }

  @Override
  public @NotNull SentryId captureException(
      @NotNull Throwable throwable, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return wrap(() -> hub.captureException(throwable, hint, callback), "captureException");
  }

  @Override
  public void captureUserFeedback(@NotNull UserFeedback userFeedback) {
    wrap(
        () -> {
          hub.captureUserFeedback(userFeedback);
          return null;
        },
        "captureUserFeedback");
  }

  @Override
  public void startSession() {
    wrap(
        () -> {
          hub.startSession();
          return null;
        },
        "startSession");
  }

  @Override
  public void endSession() {
    wrap(
        () -> {
          hub.endSession();
          return null;
        },
        "endSession");
  }

  @Override
  public void close() {
    wrap(
        () -> {
          hub.close();
          return null;
        },
        "close");
  }

  @Override
  public void close(boolean isRestarting) {
    wrap(
        () -> {
          hub.close(isRestarting);
          return null;
        },
        "close");
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {
    wrap(
        () -> {
          hub.addBreadcrumb(breadcrumb, hint);
          return null;
        },
        "addBreadcrumb");
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {
    wrap(
        () -> {
          hub.addBreadcrumb(breadcrumb);
          return null;
        },
        "addBreadcrumb");
  }

  @Override
  public void addBreadcrumb(@NotNull String message) {
    wrap(
        () -> {
          hub.addBreadcrumb(message);
          return null;
        },
        "addBreadcrumb");
  }

  @Override
  public void addBreadcrumb(@NotNull String message, @NotNull String category) {
    wrap(
        () -> {
          hub.addBreadcrumb(message, category);
          return null;
        },
        "addBreadcrumb");
  }

  @Override
  public void setLevel(@Nullable SentryLevel level) {
    wrap(
        () -> {
          hub.setLevel(level);
          return null;
        },
        "setLevel");
  }

  @Override
  public void setTransaction(@Nullable String transaction) {
    wrap(
        () -> {
          hub.setTransaction(transaction);
          return null;
        },
        "setTransaction");
  }

  @Override
  public void setUser(@Nullable User user) {
    wrap(
        () -> {
          hub.setUser(user);
          return null;
        },
        "setUser");
  }

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {
    wrap(
        () -> {
          hub.setFingerprint(fingerprint);
          return null;
        },
        "setFingerprint");
  }

  @Override
  public void clearBreadcrumbs() {
    wrap(
        () -> {
          hub.clearBreadcrumbs();
          return null;
        },
        "clearBreadcrumbs");
  }

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {
    wrap(
        () -> {
          hub.setTag(key, value);
          return null;
        },
        "setTag");
  }

  @Override
  public void removeTag(@NotNull String key) {
    wrap(
        () -> {
          hub.removeTag(key);
          return null;
        },
        "removeTag");
  }

  @Override
  public void setExtra(@NotNull String key, @NotNull String value) {
    wrap(
        () -> {
          hub.setExtra(key, value);
          return null;
        },
        "setExtra");
  }

  @Override
  public void removeExtra(@NotNull String key) {
    wrap(
        () -> {
          hub.removeExtra(key);
          return null;
        },
        "removeExtra");
  }

  @Override
  public @NotNull SentryId getLastEventId() {
    return wrap(() -> hub.getLastEventId(), "getLastEventId");
  }

  @Override
  public void pushScope() {
    wrap(
        () -> {
          hub.pushScope();
          return null;
        },
        "pushScope");
  }

  @Override
  public void popScope() {
    wrap(
        () -> {
          hub.popScope();
          return null;
        },
        "popScope");
  }

  @Override
  public void withScope(@NotNull ScopeCallback callback) {
    wrap(
        () -> {
          hub.withScope(callback);
          return null;
        },
        "withScope");
  }

  @Override
  public void configureScope(@NotNull ScopeCallback callback) {
    wrap(
        () -> {
          hub.configureScope(callback);
          return null;
        },
        "configureScope");
  }

  @Override
  public void bindClient(@NotNull ISentryClient client) {
    wrap(
        () -> {
          hub.bindClient(client);
          return null;
        },
        "bindClient");
  }

  @Override
  public boolean isHealthy() {
    return wrap(() -> hub.isHealthy(), "isHealthy");
  }

  @Override
  public void flush(long timeoutMillis) {
    wrap(
        () -> {
          hub.flush(timeoutMillis);
          return null;
        },
        "flush");
  }

  @Override
  public @NotNull IHub clone() {
    return wrap(() -> new TracingHub(hub.clone()), "clone");
  }

  @Override
  public @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable Hint hint,
      @Nullable ProfilingTraceData profilingTraceData) {
    return wrap(
        () -> hub.captureTransaction(transaction, traceContext, hint, profilingTraceData),
        "captureTransaction");
  }

  @Override
  public @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable Hint hint) {
    return wrap(
        () -> hub.captureTransaction(transaction, traceContext, hint), "captureTransaction");
  }

  @Override
  public @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction, @Nullable Hint hint) {
    return wrap(() -> hub.captureTransaction(transaction, hint), "captureTransaction");
  }

  @Override
  public @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction, @Nullable TraceContext traceContext) {
    return wrap(() -> hub.captureTransaction(transaction, traceContext), "captureTransaction");
  }

  @Override
  public @NotNull ITransaction startTransaction(@NotNull TransactionContext transactionContexts) {
    return wrap(() -> hub.startTransaction(transactionContexts), "startTransaction");
  }

  @Override
  public @NotNull ITransaction startTransaction(@NotNull String name, @NotNull String operation) {
    return wrap(() -> hub.startTransaction(name, operation), "startTransaction");
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull String name,
      @NotNull String operation,
      @NotNull TransactionOptions transactionOptions) {
    return wrap(
        () -> hub.startTransaction(name, operation, transactionOptions), "startTransaction");
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContext,
      @NotNull TransactionOptions transactionOptions) {
    return wrap(
        () -> hub.startTransaction(transactionContext, transactionOptions), "startTransaction");
  }

  @SuppressWarnings("deprecation")
  @Override
  public @Nullable SentryTraceHeader traceHeaders() {
    return wrap(() -> hub.traceHeaders(), "traceHeaders");
  }

  @Override
  public void setSpanContext(
      @NotNull Throwable throwable, @NotNull ISpan span, @NotNull String transactionName) {
    wrap(
        () -> {
          hub.setSpanContext(throwable, span, transactionName);
          return null;
        },
        "setSpanContext");
  }

  @Override
  public @Nullable ISpan getSpan() {
    return wrap(() -> hub.getSpan(), "getSpan");
  }

  @Override
  public @Nullable ITransaction getTransaction() {
    return wrap(() -> hub.getTransaction(), "getTransaction");
  }

  @Override
  public @NotNull SentryOptions getOptions() {
    return wrap(() -> hub.getOptions(), "getOptions");
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return wrap(() -> hub.isCrashedLastRun(), "isCrashedLastRun");
  }

  @Override
  public void reportFullyDisplayed() {
    wrap(
        () -> {
          hub.reportFullyDisplayed();
          return null;
        },
        "reportFullyDisplayed");
  }

  @SuppressWarnings("deprecation")
  @Override
  public void reportFullDisplayed() {
    wrap(
        () -> {
          hub.reportFullDisplayed();
          return null;
        },
        "reportFullDisplayed");
  }

  @Override
  public @Nullable TransactionContext continueTrace(
      @Nullable String sentryTrace, @Nullable List<String> baggageHeaders) {
    return wrap(() -> hub.continueTrace(sentryTrace, baggageHeaders), "continueTrace");
  }

  @Override
  public @Nullable SentryTraceHeader getTraceparent() {
    return wrap(() -> hub.getTraceparent(), "getTraceparent");
  }

  @Override
  public @Nullable BaggageHeader getBaggage() {
    return wrap(() -> hub.getBaggage(), "getBaggage");
  }

  @Override
  public @NotNull SentryId captureCheckIn(@NotNull CheckIn checkIn) {
    return wrap(() -> hub.captureCheckIn(checkIn), "captureCheckIn");
  }

  @Override
  public @NotNull SentryId captureReplay(@NotNull SentryReplayEvent replay, @Nullable Hint hint) {
    return wrap(() -> hub.captureReplay(replay, hint), "captureReplay");
  }

  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return wrap(() -> hub.getRateLimiter(), "getRateLimiter");
  }

  @Override
  public @NotNull MetricsApi metrics() {
    return wrap(() -> hub.metrics(), "metrics");
  }
}
