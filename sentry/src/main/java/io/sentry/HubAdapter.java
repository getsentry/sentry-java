package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

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
  public SentryId captureEvent(SentryEvent event, @Nullable Object hint) {
    return Sentry.captureEvent(event, hint);
  }

  @Override
  public SentryId captureMessage(String message, SentryLevel level) {
    return Sentry.captureMessage(message, level);
  }

  @ApiStatus.Internal
  @Override
  public SentryId captureEnvelope(SentryEnvelope envelope, @Nullable Object hint) {
    return Sentry.getCurrentHub().captureEnvelope(envelope, hint);
  }

  @Override
  public SentryId captureException(Throwable throwable, @Nullable Object hint) {
    return Sentry.captureException(throwable, hint);
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
  public void close() {
    Sentry.close();
  }

  @Override
  public void addBreadcrumb(Breadcrumb breadcrumb, @Nullable Object hint) {
    Sentry.addBreadcrumb(breadcrumb, hint);
  }

  @Override
  public void setLevel(SentryLevel level) {
    Sentry.setLevel(level);
  }

  @Override
  public void setTransaction(String transaction) {
    Sentry.setTransaction(transaction);
  }

  @Override
  public void setUser(User user) {
    Sentry.setUser(user);
  }

  @Override
  public void setFingerprint(List<String> fingerprint) {
    Sentry.setFingerprint(fingerprint);
  }

  @Override
  public void clearBreadcrumbs() {
    Sentry.clearBreadcrumbs();
  }

  @Override
  public void setTag(String key, String value) {
    Sentry.setTag(key, value);
  }

  @Override
  public void removeTag(String key) {
    Sentry.removeTag(key);
  }

  @Override
  public void setExtra(String key, String value) {
    Sentry.setExtra(key, value);
  }

  @Override
  public void removeExtra(String key) {
    Sentry.removeExtra(key);
  }

  @Override
  public SentryId getLastEventId() {
    return Sentry.getLastEventId();
  }

  @Override
  public void pushScope() {
    Sentry.pushScope();
  }

  @Override
  public void popScope() {
    Sentry.popScope();
  }

  @Override
  public void withScope(ScopeCallback callback) {
    Sentry.withScope(callback);
  }

  @Override
  public void configureScope(ScopeCallback callback) {
    Sentry.configureScope(callback);
  }

  @Override
  public void bindClient(ISentryClient client) {
    Sentry.bindClient(client);
  }

  @Override
  public void flush(long timeoutMillis) {
    Sentry.flush(timeoutMillis);
  }

  @Override
  public IHub clone() {
    return Sentry.getCurrentHub().clone();
  }

  @Override
  public SentryId captureTransaction(Transaction transaction, Object hint) {
    return Sentry.captureTransaction(transaction, hint);
  }

  @Override
  public Transaction startTransaction(TransactionContexts transactionContexts) {
    return Sentry.startTransaction(transactionContexts);
  }
}
