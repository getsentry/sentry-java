package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class Platform {
  // All system properties on Android:
  // https://developer.android.com/reference/java/lang/System#getProperties()
  private static final boolean isAndroid =
      "The Android Project".equals(System.getProperty("java.vendor"));

  public static boolean isAndroid() {
    return isAndroid;
  }

  public static boolean isJvm() {
    return !isAndroid;
  }
}
