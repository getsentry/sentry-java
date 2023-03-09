package io.sentry.android.core;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.content.Context.ACTIVITY_SERVICE;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.DisplayMetrics;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * Get the device's current kernel version, as a string. Attempts to read /proc/version, and falls
   * back to the 'os.version' System Property.
   *
   * @return the device's current kernel version, as a string
   */
  @SuppressWarnings("DefaultCharset")
  static @Nullable String getKernelVersion(final @NotNull ILogger logger) {
    // its possible to try to execute 'uname' and parse it or also another unix commands or even
    // looking for well known root installed apps
    String errorMsg = "Exception while attempting to read kernel information";
    String defaultVersion = System.getProperty("os.version");

    File file = new File("/proc/version");
    if (!file.canRead()) {
      return defaultVersion;
    }
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      return br.readLine();
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, errorMsg, e);
    }

    return defaultVersion;
  }

  @SuppressWarnings("deprecation")
  static @Nullable Map<String, String> getSideLoadedInfo(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    String packageName = null;
    try {
      final PackageInfo packageInfo = getPackageInfo(context, logger, buildInfoProvider);
      final PackageManager packageManager = context.getPackageManager();

      if (packageInfo != null && packageManager != null) {
        packageName = packageInfo.packageName;

        // getInstallSourceInfo requires INSTALL_PACKAGES permission which is only given to system
        // apps.
        final String installerPackageName = packageManager.getInstallerPackageName(packageName);

        final Map<String, String> sideLoadedInfo = new HashMap<>();

        if (installerPackageName != null) {
          sideLoadedInfo.put("isSideLoaded", "false");
          // could be amazon, google play etc
          sideLoadedInfo.put("installerStore", installerPackageName);
        } else {
          // if it's installed via adb, system apps or untrusted sources
          sideLoadedInfo.put("isSideLoaded", "true");
        }

        return sideLoadedInfo;
      }
    } catch (IllegalArgumentException e) {
      // it'll never be thrown as we are querying its own App's package.
      logger.log(SentryLevel.DEBUG, "%s package isn't installed.", packageName);
    }

    return null;
  }

  /**
   * Get the human-facing Application name.
   *
   * @return Application name
   */
  static @Nullable String getApplicationName(
      final @NotNull Context context, final @NotNull ILogger logger) {
    try {
      ApplicationInfo applicationInfo = context.getApplicationInfo();
      int stringId = applicationInfo.labelRes;
      if (stringId == 0) {
        if (applicationInfo.nonLocalizedLabel != null) {
          return applicationInfo.nonLocalizedLabel.toString();
        }
        return context.getPackageManager().getApplicationLabel(applicationInfo).toString();
      } else {
        return context.getString(stringId);
      }
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Error getting application name.", e);
    }

    return null;
  }

  /**
   * Get the DisplayMetrics object for the current application.
   *
   * @return the DisplayMetrics object for the current application
   */
  static @Nullable DisplayMetrics getDisplayMetrics(
      final @NotNull Context context, final @NotNull ILogger logger) {
    try {
      return context.getResources().getDisplayMetrics();
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Error getting DisplayMetrics.", e);
      return null;
    }
  }

  /**
   * Fake the device family by using the first word in the Build.MODEL. Works well in most cases...
   * "Nexus 6P" -> "Nexus", "Galaxy S7" -> "Galaxy".
   *
   * @return family name of the device, as best we can tell
   */
  static @Nullable String getFamily(final @NotNull ILogger logger) {
    try {
      return Build.MODEL.split(" ", -1)[0];
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Error getting device family.", e);
      return null;
    }
  }

  @SuppressLint("NewApi") // we're wrapping into if-check with sdk version
  static @Nullable String getDeviceName(
      final @NotNull Context context, final @NotNull BuildInfoProvider buildInfoProvider) {
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return Settings.Global.getString(context.getContentResolver(), "device_name");
    } else {
      return null;
    }
  }

  @SuppressWarnings("deprecation")
  @SuppressLint("NewApi") // we're wrapping into if-check with sdk version
  static @NotNull String[] getArchitectures(final @NotNull BuildInfoProvider buildInfoProvider) {
    String[] supportedAbis;
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.LOLLIPOP) {
      supportedAbis = Build.SUPPORTED_ABIS;
    } else {
      supportedAbis = new String[] {Build.CPU_ABI, Build.CPU_ABI2};
    }
    return supportedAbis;
  }

  /**
   * Get MemoryInfo object representing the memory state of the application.
   *
   * @return MemoryInfo object representing the memory state of the application
   */
  static @Nullable ActivityManager.MemoryInfo getMemInfo(
      final @NotNull Context context, final @NotNull ILogger logger) {
    try {
      ActivityManager actManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
      ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
      if (actManager != null) {
        actManager.getMemoryInfo(memInfo);
        return memInfo;
      }
      logger.log(SentryLevel.INFO, "Error getting MemoryInfo.");
      return null;
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Error getting MemoryInfo.", e);
      return null;
    }
  }
}
