package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Scope observer that tracks changes on Scope. All methods are "default", so implementors can
 * subscribe to only those properties, that they are interested in.
 */
public interface IScopeObserver {
  void setUser(@Nullable User user);

  void addBreadcrumb(@NotNull Breadcrumb crumb);

  void setBreadcrumbs(@NotNull Collection<Breadcrumb> breadcrumbs);

  void setTag(@NotNull String key, @NotNull String value);

  void removeTag(@NotNull String key);

  void setTags(@NotNull Map<String, @NotNull String> tags);

  void setExtra(@NotNull String key, @NotNull String value);

  void removeExtra(@NotNull String key);

  void setExtras(@NotNull Map<String, @NotNull Object> extras);

  void setRequest(@Nullable Request request);

  void setFingerprint(@NotNull Collection<String> fingerprint);

  void setLevel(@Nullable SentryLevel level);

  void setContexts(@NotNull Contexts contexts);

  void setTransaction(@Nullable String transaction);

  void setTrace(@Nullable SpanContext spanContext, @NotNull IScope scope);

  void setReplayId(@NotNull SentryId replayId);
}
