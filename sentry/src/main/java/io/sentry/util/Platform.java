package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class Platform {
  private static boolean isAndroid;
  private static boolean isJavaNinePlus;

  static {
    // System#getProperty can throw an exception if there is a security manager is configured and
    // does not allow accessing system properties
    try {
      // All system properties on Android:
      // https://developer.android.com/reference/java/lang/System#getProperties()
      isAndroid = "The Android Project".equals(System.getProperty("java.vendor"));
    } catch (Throwable e) {
      isAndroid = false;
    }

    try {
      final @Nullable String javaStringVersion = System.getProperty("java.specification.version");
      if (javaStringVersion != null) {
        final @NotNull double javaVersion = Double.valueOf(javaStringVersion);
        isJavaNinePlus = javaVersion >= 9.0;
      } else {
        isJavaNinePlus = false;
      }
    } catch (Throwable e) {
      isJavaNinePlus = false;
    }
  }

  public static boolean isAndroid() {
    return isAndroid;
  }

  public static boolean isJvm() {
    return !isAndroid;
  }

  public static boolean isJavaNinePlus() {
    return isJavaNinePlus;
  }
}
