package io.sentry;

import io.sentry.protocol.User;

public interface IScopeObserver {
  void setUser(User user);

  void addBreadcrumb(Breadcrumb crumb);

  void setTag(String key, String value);
}
