package io.sentry.util;

import io.sentry.ISentryLifecycleToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LifecycleHelper {

  public static void close(final @Nullable Object tokenObject) {
    if (tokenObject != null && tokenObject instanceof ISentryLifecycleToken) {
      final @NotNull ISentryLifecycleToken token = (ISentryLifecycleToken) tokenObject;
      token.close();
    }
  }
}
