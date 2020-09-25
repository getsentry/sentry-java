package io.sentry.android.ndk;

import io.sentry.Breadcrumb;
import io.sentry.DateUtils;
import io.sentry.IScopeObserver;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NdkScopeObserver implements IScopeObserver {

  private final @NotNull SentryOptions options;

  public NdkScopeObserver(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public void setUser(final @Nullable User user) {
    try {
      if (user == null) {
        // remove user if its null
        nativeRemoveUser();
      } else {
        nativeSetUser(user.getId(), user.getEmail(), user.getIpAddress(), user.getUsername());
      }
    } catch (Exception e) {
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
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, e, "Breadcrumb data is not serializable.");
      }

      nativeAddBreadcrumb(
          level, crumb.getMessage(), crumb.getCategory(), crumb.getType(), timestamp, data);
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync addBreadcrumb has an error.");
    }
  }

  @Override
  public void setTag(final @NotNull String key, final @Nullable String value) {
    try {
      nativeSetTag(key, value);
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync setTag(%s) has an error.", key);
    }
  }

  @Override
  public void removeTag(final @NotNull String key) {
    try {
      nativeRemoveTag(key);
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync removeTag(%s) has an error.", key);
    }
  }

  @Override
  public void setExtra(final @NotNull String key, final @Nullable String value) {
    try {
      nativeSetExtra(key, value);
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync setExtra(%s) has an error.", key);
    }
  }

  @Override
  public void removeExtra(final @NotNull String key) {
    try {
      nativeRemoveExtra(key);
    } catch (Exception e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, e, "Scope sync removeExtra(%s) has an error.", key);
    }
  }

  public static native void nativeSetTag(String key, String value);

  public static native void nativeRemoveTag(String key);

  public static native void nativeSetExtra(String key, String value);

  public static native void nativeRemoveExtra(String key);

  public static native void nativeSetUser(
      String id, String email, String ipAddress, String username);

  public static native void nativeRemoveUser();

  // TODO: missing data field
  public static native void nativeAddBreadcrumb(
      String level, String message, String category, String type, String timestamp, String data);
}
