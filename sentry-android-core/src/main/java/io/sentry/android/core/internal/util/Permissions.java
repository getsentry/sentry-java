package io.sentry.android.core.internal.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class Permissions {

  private Permissions() {}

  public static boolean hasPermission(
      final @NotNull Context context, final @NotNull String permission) {
    Objects.requireNonNull(context, "The application context is required.");

    return context.checkPermission(permission, Process.myPid(), Process.myUid())
        == PackageManager.PERMISSION_GRANTED;
  }
}
