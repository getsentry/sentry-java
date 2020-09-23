package io.sentry.android.ndk;

import io.sentry.Breadcrumb;
import io.sentry.IScopeObserver;
import io.sentry.protocol.User;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NdkScopeObserver implements IScopeObserver {
  @Override
  public void setUser(final @Nullable User user) {
    // Unset user from the scope
    if (user == null) {
      nativeRemoveUser();
    } else {
      nativeSetUser(user.getId(), user.getEmail(), user.getIpAddress(), user.getUsername());
    }
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb crumb) {
    String level = null;
    if (crumb.getLevel() != null) {
      level = crumb.getLevel().name().toLowerCase(Locale.ROOT);
    }
    nativeAddBreadcrumb(level, crumb.getMessage(), crumb.getCategory(), crumb.getType());
  }

  @Override
  public void setTag(final @NotNull String key, final @Nullable String value) {
    nativeSetTag(key, value);
  }

  @Override
  public void removeTag(final @NotNull String key) {
    nativeRemoveTag(key);
  }

  @Override
  public void setExtra(final @NotNull String key, final @Nullable String value) {
    nativeSetExtra(key, value);
  }

  @Override
  public void removeExtra(final @NotNull String key) {
    nativeRemoveExtra(key);
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
      String level, String message, String category, String type);
}
