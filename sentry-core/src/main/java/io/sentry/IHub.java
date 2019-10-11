package io.sentry;

import io.sentry.protocol.SentryId;

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
