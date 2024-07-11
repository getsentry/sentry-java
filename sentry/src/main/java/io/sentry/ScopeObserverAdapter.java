package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ScopeObserverAdapter implements IScopeObserver {
  @Override
  public void setUser(@Nullable User user) {}

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb crumb) {}

  @Override
  public void setBreadcrumbs(@NotNull Collection<Breadcrumb> breadcrumbs) {}

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {}

  @Override
  public void removeTag(@NotNull String key) {}

  @Override
  public void setTags(@NotNull Map<String, @NotNull String> tags) {}

  @Override
  public void setExtra(@NotNull String key, @NotNull String value) {}

  @Override
  public void removeExtra(@NotNull String key) {}

  @Override
  public void setExtras(@NotNull Map<String, @NotNull Object> extras) {}

  @Override
  public void setRequest(@Nullable Request request) {}

  @Override
  public void setFingerprint(@NotNull Collection<String> fingerprint) {}

  @Override
  public void setLevel(@Nullable SentryLevel level) {}

  @Override
  public void setContexts(@NotNull Contexts contexts) {}

  @Override
  public void setTransaction(@Nullable String transaction) {}

  @Override
  public void setTrace(@Nullable SpanContext spanContext, @NotNull IScope scope) {}

  @Override
  public void setReplayId(@NotNull SentryId replayId) {}
}
