package io.sentry.android.core;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.util.List;
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
  static PackageInfo getPackageInfo(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    return getPackageInfo(context, 0, logger, buildInfoProvider);
  }

  /**
   * Return the Application's PackageInfo with the specified flags if possible, or null.
   *
   * @return the Application's PackageInfo if possible, or null
   */
  @SuppressLint("NewApi")
  @Nullable
  @SuppressWarnings("deprecation")
  static PackageInfo getPackageInfo(
      final @NotNull Context context,
      final int flags,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    try {
      if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.TIRAMISU) {
        return context
            .getPackageManager()
            .getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(flags));
      } else {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
      }
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Error getting package info.", e);
      return null;
    }
  }

  /**
   * Return the Application's ApplicationInfo if possible. Throws @{@link
   * android.content.pm.PackageManager.NameNotFoundException} if the package is not found.
   *
   * @return the Application's ApplicationInfo if possible, or throws
   */
  @SuppressLint("NewApi")
  @NotNull
  @SuppressWarnings("deprecation")
  static ApplicationInfo getApplicationInfo(
      final @NotNull Context context,
      final long flag,
      final @NotNull BuildInfoProvider buildInfoProvider)
      throws PackageManager.NameNotFoundException {
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.TIRAMISU) {
      return context
          .getPackageManager()
          .getApplicationInfo(
              context.getPackageName(), PackageManager.ApplicationInfoFlags.of(flag));
    } else {
      return context
          .getPackageManager()
          .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
    }
  }

  /**
   * Returns the App's version code based on the PackageInfo
   *
   * @param packageInfo the PackageInfo class
   * @return the versionCode or LongVersionCode based on your API version
   */
  @SuppressLint("NewApi")
  @NotNull
  static String getVersionCode(
      final @NotNull PackageInfo packageInfo, final @NotNull BuildInfoProvider buildInfoProvider) {
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.P) {
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

  /**
   * Check if the Started process has IMPORTANCE_FOREGROUND importance which means that the process
   * will start an Activity.
   *
   * @return true if IMPORTANCE_FOREGROUND and false otherwise
   */
  static boolean isForegroundImportance(final @NotNull Context context) {
    try {
      final Object service = context.getSystemService(Context.ACTIVITY_SERVICE);
      if (service instanceof ActivityManager) {
        final ActivityManager activityManager = (ActivityManager) service;
        final List<ActivityManager.RunningAppProcessInfo> runningAppProcesses =
            activityManager.getRunningAppProcesses();

        if (runningAppProcesses != null) {
          final int myPid = Process.myPid();
          for (final ActivityManager.RunningAppProcessInfo processInfo : runningAppProcesses) {
            if (processInfo.pid == myPid) {
              if (processInfo.importance == IMPORTANCE_FOREGROUND) {
                return true;
              }
              break;
            }
          }
        }
      }
    } catch (SecurityException ignored) {
      // happens for isolated processes
    } catch (Throwable ignored) {
      // should never happen
    }
    return false;
  }
}
