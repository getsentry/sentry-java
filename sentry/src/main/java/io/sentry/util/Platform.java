package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class Platform {
  private static boolean isAndroid;

  static {
    // System#getProperty can throw an exception if there is a security manager is configured and
    // does not allow accessing system properties
    try {
      // All system properties on Android:
      // https://developer.android.com/reference/java/lang/System#getProperties()
      isAndroid = "The Android Project".equals(System.getProperty("java.vendor"));
    } catch (Exception e) {
      isAndroid = false;
    }
  }

  public static boolean isAndroid() {
    return isAndroid;
  }

  public static boolean isJvm() {
    return !isAndroid;
  }
}
