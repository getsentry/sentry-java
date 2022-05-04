package io.sentry.android.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ContextUtils {

  private ContextUtils() {}

  /**
   * Return the Application's PackageInfo if possible, or null.
   *
   * @return the Application's PackageInfo if possible, or null
   */
  @Nullable
  static PackageInfo getPackageInfo(final @NotNull Context context, final @NotNull ILogger logger) {
    return getPackageInfo(context, 0, logger);
  }

  /**
   * Return the Application's PackageInfo with the specified flags if possible, or null.
   *
   * @return the Application's PackageInfo if possible, or null
   */
  @Nullable
  static PackageInfo getPackageInfo(
      final @NotNull Context context, final int flags, final @NotNull ILogger logger) {
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Error getting package info.", e);
      return null;
    }
  }

  /**
   * Returns the App's version code based on the PackageInfo
   *
   * @param packageInfo the PackageInfo class
   * @return the versionCode or LongVersionCode based on your API version
   */
  @NotNull
  static String getVersionCode(final @NotNull PackageInfo packageInfo) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return Long.toString(packageInfo.getLongVersionCode());
    }
    return getVersionCodeDep(packageInfo);
  }

  /**
   * Returns the App's version name based on the PackageInfo
   *
   * @param packageInfo the PackageInfo class
   * @return the versionName
   */
  @Nullable
  static String getVersionName(final @NotNull PackageInfo packageInfo) {
    return packageInfo.versionName;
  }

  @SuppressWarnings("deprecation")
  private static @NotNull String getVersionCodeDep(final @NotNull PackageInfo packageInfo) {
    return Integer.toString(packageInfo.versionCode);
  }
}
