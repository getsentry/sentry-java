package io.sentry;

import io.sentry.protocol.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Observer for the sync. of Scopes across SDKs */
public interface IScopeObserver {
  void setUser(@Nullable User user);

  void addBreadcrumb(@NotNull Breadcrumb crumb);

  void setTag(@NotNull String key, @Nullable String value);

  void removeTag(@NotNull String key);

  void setExtra(@NotNull String key, @NotNull String value);

  void removeExtra(@NotNull String key);
}
