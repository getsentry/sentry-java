package io.sentry.android.ndk;

import io.sentry.Breadcrumb;
import io.sentry.IScopeObserver;
import io.sentry.protocol.User;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NdkScopeObserver implements IScopeObserver {
  @Override
  public void setUser(@Nullable User user) {
    // Unset user from the scope
    if (user == null) {
      nativeSetUser(null, null, null, null);
    } else {
      nativeSetUser(user.getId(), user.getEmail(), user.getIpAddress(), user.getUsername());
    }
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb crumb) {
    String level = null;
    if (crumb.getLevel() != null) {
      level = crumb.getLevel().name().toLowerCase(Locale.ROOT);
    }
    nativeAddBreadcrumb(level, crumb.getMessage(), crumb.getCategory(), crumb.getType());
  }

  @Override
  public void setTag(@NotNull String key, @Nullable String value) {
    nativeSetTag(key, value);
  }

  public static native void nativeSetTag(String key, String value);

  public static native void nativeSetUser(
      String id, String email, String ipAddress, String username);

  public static native void nativeAddBreadcrumb(
      String id, String email, String ipAddress, String username);
}
