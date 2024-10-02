package io.sentry.android.ndk;

import io.sentry.Breadcrumb;
import io.sentry.DateUtils;
import io.sentry.ScopeObserverAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NdkScopeObserver extends ScopeObserverAdapter {

  private final @NotNull SentryOptions options;
  private final @NotNull INativeScope nativeScope;

  public NdkScopeObserver(final @NotNull SentryOptions options) {
    this(options, new NativeScope());
  }

  NdkScopeObserver(final @NotNull SentryOptions options, final @NotNull INativeScope nativeScope) {
    this.options = Objects.requireNonNull(options, "The SentryOptions object is required.");
    this.nativeScope = Objects.requireNonNull(nativeScope, "The NativeScope object is required.");
  }

  @Override
  public void setUser(final @Nullable User user) {
    try {
      if (user == null) {
        // remove user if its null
        nativeScope.removeUser();
      } else {
        nativeScope.setUser(user.getId(), user.getEmail(), user.getIpAddress(), user.getUsername());
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync setUser has an error.");
    }
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb crumb) {
    try {
      String level = null;
      if (crumb.getLevel() != null) {
        level = crumb.getLevel().name().toLowerCase(Locale.ROOT);
      }
      final String timestamp = DateUtils.getTimestamp(crumb.getTimestamp());

      String data = null;
      try {
        final Map<String, Object> dataRef = crumb.getData();
        if (!dataRef.isEmpty()) {
          data = options.getSerializer().serialize(dataRef);
        }
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, e, "Breadcrumb data is not serializable.");
      }

      nativeScope.addBreadcrumb(
          level, crumb.getMessage(), crumb.getCategory(), crumb.getType(), timestamp, data);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync addBreadcrumb has an error.");
    }
  }

  @Override
  public void setTag(final @NotNull String key, final @NotNull String value) {
    try {
      nativeScope.setTag(key, value);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync setTag(%s) has an error.", key);
    }
  }

  @Override
  public void removeTag(final @NotNull String key) {
    try {
      nativeScope.removeTag(key);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync removeTag(%s) has an error.", key);
    }
  }

  @Override
  public void setExtra(final @NotNull String key, final @NotNull String value) {
    try {
      nativeScope.setExtra(key, value);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync setExtra(%s) has an error.", key);
    }
  }

  @Override
  public void removeExtra(final @NotNull String key) {
    try {
      nativeScope.removeExtra(key);
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, e, "Scope sync removeExtra(%s) has an error.", key);
    }
  }
}
