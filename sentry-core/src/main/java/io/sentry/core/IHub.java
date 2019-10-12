package io.sentry.core;

import io.sentry.core.protocol.SentryId;

public interface IHub extends ISentryClient {
  void addBreadcrumb(Breadcrumb breadcrumb);

  SentryId getLastEventId();

  void pushScope();

  void popScope();

  void withScope(ScopeCallback callback);

  void configureScope(ScopeCallback callback);

  void bindClient(SentryClient client);

  void flush(long timeoutMills);

  IHub clone();
}
