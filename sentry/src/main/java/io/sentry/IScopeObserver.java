package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.User;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Scope observer that tracks changes on Scope.
 * All methods are "default", so implementors can subscribe to only those properties, that they are
 * interested in.
 */
public interface IScopeObserver {
  default void setUser(@Nullable User user) {}

  default void addBreadcrumb(@NotNull Breadcrumb crumb) {}

  default void setBreadcrumbs(@NotNull Collection<Breadcrumb> breadcrumbs) {}

  default void setTag(@NotNull String key, @NotNull String value) {}

  default void removeTag(@NotNull String key) {}

  default void setTags(@NotNull Map<String, @NotNull String> tags) {}

  default void setExtra(@NotNull String key, @NotNull String value) {}

  default void removeExtra(@NotNull String key) {}

  default void setExtras(@NotNull Map<String, @NotNull Object> extras) {}

  default void setRequest(@Nullable Request request) {}

  default void setFingerprint(@NotNull Collection<String> fingerprint) {}

  default void setLevel(@Nullable SentryLevel level) {}

  default void setContexts(@NotNull Contexts contexts) {}

  default void setTransaction(@Nullable String transaction) {}

  default void setTrace(@Nullable SpanContext spanContext) {}

  default void setRelease(@Nullable String release) {}

  default void setProguardUuid(@Nullable String proguardUuid) {}

  default void setSdkVersion(@Nullable SdkVersion sdkVersion) {}

  default void setEnvironment(@Nullable String environment) {}

  default void setDist(@Nullable String dist) {}
}
