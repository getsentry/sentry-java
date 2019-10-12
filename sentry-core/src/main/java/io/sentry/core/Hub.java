package io.sentry.core;

import io.sentry.core.protocol.SentryId;

public class Hub implements IHub {
  private SentryOptions options;
  private volatile boolean isEnabled;

  public Hub(SentryOptions options) {
    this.options = options;
    isEnabled = true;
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public SentryId captureEvent(SentryEvent event) {
    return null;
  }

  @Override
  public SentryId captureMessage(String message) {
    return null;
  }

  @Override
  public SentryId captureException(Throwable throwable) {
    return null;
  }

  @Override
  public void close() {
    isEnabled = false;
  }

  @Override
  public void addBreadcrumb(Breadcrumb breadcrumb) {}

  @Override
  public SentryId getLastEventId() {
    return null;
  }

  @Override
  public void pushScope() {}

  @Override
  public void popScope() {}

  @Override
  public void withScope(ScopeCallback callback) {}

  @Override
  public void configureScope(ScopeCallback callback) {}

  @Override
  public void bindClient(SentryClient client) {}

  @Override
  public void flush(long timeoutMills) {}

  @Override
  public IHub clone() {
    return new Hub(options);
  }
}
