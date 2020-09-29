package io.sentry;

import io.sentry.protocol.User;

/** Observer for the sync. of Scopes across SDKs */
public interface IScopeObserver {
  void setUser(User user);

  void addBreadcrumb(Breadcrumb crumb);

  void setTag(String key, String value);

  void removeTag(String key);

  void setExtra(String key, String value);

  void removeExtra(String key);
}
