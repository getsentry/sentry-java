package io.sentry.android.core.internal.util;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.util.Objects;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class RootChecker {

  /** the UTF-8 Charset */
  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull Context context;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull ILogger logger;

  private final @NotNull String[] rootFiles;

  private final @NotNull String[] rootPackages;

  private final @NotNull Runtime runtime;

  public RootChecker(
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull ILogger logger) {
    this(
        context,
        buildInfoProvider,
        logger,
        new String[] {
          "/system/app/Superuser.apk",
          "/sbin/su",
          "/system/bin/su",
          "/system/xbin/su",
          "/data/local/xbin/su",
          "/data/local/bin/su",
          "/system/sd/xbin/su",
          "/system/bin/failsafe/su",
          "/data/local/su",
          "/su/bin/su",
          "/su/bin",
          "/system/xbin/daemonsu"
        },
        new String[] {
          "com.devadvance.rootcloak",
          "com.devadvance.rootcloakplus",
          "com.koushikdutta.superuser",
          "com.thirdparty.superuser",
          "eu.chainfire.supersu", // SuperSU
          "com.noshufou.android.su" // superuser
        },
        Runtime.getRuntime());
  }

  RootChecker(
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull ILogger logger,
      final @NotNull String[] rootFiles,
      final @NotNull String[] rootPackages,
      final @NotNull Runtime runtime) {
    this.context = Objects.requireNonNull(context, "The application context is required.");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "The BuildInfoProvider is required.");
    this.logger = Objects.requireNonNull(logger, "The Logger is required.");
    this.rootFiles = Objects.requireNonNull(rootFiles, "The root Files are required.");
    this.rootPackages = Objects.requireNonNull(rootPackages, "The root packages are required.");
    this.runtime = Objects.requireNonNull(runtime, "The Runtime is required.");
  }

  /**
   * Check if the device is rooted or not
   * https://medium.com/@thehimanshugoel/10-best-security-practices-in-android-applications-that-every-developer-must-know-99c8cd07c0bb
   *
   * @return whether the device is rooted or not
   */
  public boolean isDeviceRooted() {
    return checkTestKeys() || checkRootFiles() || checkSUExist() || checkRootPackages(logger);
  }

  /**
   * Android Roms from Google are build with release-key tags. If test-keys are present, this can
   * mean that the Android build on the device is either a developer build or an unofficial Google
   * build.
   *
   * @return whether if it contains test keys or not
   */
  private boolean checkTestKeys() {
    final String buildTags = buildInfoProvider.getBuildTags();
    return buildTags != null && buildTags.contains("test-keys");
  }

  /**
   * Often the rooted device have the following files . This method will check whether the device is
   * having these files or not
   *
   * @return whether if the root files exist or not
   */
  private boolean checkRootFiles() {
    for (final String path : rootFiles) {
      try {
        if (new File(path).exists()) {
          return true;
        }
      } catch (RuntimeException e) {
        if (logger.isEnabled(ERROR)) {
          logger.log(
              SentryLevel.ERROR, e, "Error when trying to check if root file %s exists.", path);
        }
      }
    }
    return false;
  }

  /**
   * this will check if SU(Super User) exist or not
   *
   * @return whether su exists or not
   */
  private boolean checkSUExist() {
    Process process = null;
    final String[] su = {"/system/xbin/which", "su"};

    try {
      process = runtime.exec(su);

      try (final BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {
        return reader.readLine() != null;
      }
    } catch (IOException e) {
      if (logger.isEnabled(SentryLevel.DEBUG)) {
        logger.log(SentryLevel.DEBUG, "SU isn't found on this Device.");
      }
    } catch (Throwable e) {
      if (logger.isEnabled(DEBUG)) {
        logger.log(SentryLevel.DEBUG, "Error when trying to check if SU exists.", e);
      }
    } finally {
      if (process != null) {
        process.destroy();
      }
    }
    return false;
  }

  /**
   * some application hide the root status of the android device. This will check for those files
   *
   * @return whether the root packages exist or not
   */
  @SuppressLint("NewApi")
  @SuppressWarnings("deprecation")
  private boolean checkRootPackages(final @NotNull ILogger logger) {
    BuildInfoProvider buildInfoProvider = new BuildInfoProvider(logger);
    final PackageManager pm = context.getPackageManager();
    if (pm != null) {
      for (final String pkg : rootPackages) {
        try {
          if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0));
          } else {
            pm.getPackageInfo(pkg, 0);
          }
          return true;
        } catch (PackageManager.NameNotFoundException ignored) {
          // fine, package doesn't exist.
        }
      }
    }
    return false;
  }
}
