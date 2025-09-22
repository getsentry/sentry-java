/*
 * Root detection implementation adapted from Ravencoin Android:
 * https://github.com/Menwitz/ravencoin-android/blob/7b68378c046e2fd0d6f30cea59cbd87fcb6db12d/app/src/main/java/com/ravencoin/tools/security/RootHelper.java
 *
 * RavenWallet
 * <p/>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 5/19/16.
 * Copyright (c) 2016 breadwallet LLC
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.sentry.android.core.internal.util;

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
          "/sbin/su",
          "/data/local/xbin/su",
          "/system/bin/su",
          "/system/xbin/su",
          "/data/local/bin/su",
          "/system/app/Superuser.apk",
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
   *
   * @return whether the device is rooted or not
   */
  public boolean isDeviceRooted() {
    return checkRootA() || checkRootB() || checkRootC() || checkRootPackages(logger);
  }

  /**
   * Android Roms from Google are build with release-key tags. If test-keys are present, this can
   * mean that the Android build on the device is either a developer build or an unofficial Google
   * build.
   *
   * @return whether if it contains test keys or not
   */
  private boolean checkRootA() {
    final String buildTags = buildInfoProvider.getBuildTags();
    return buildTags != null && buildTags.contains("test-keys");
  }

  /**
   * Often the rooted device have the following files . This method will check whether the device is
   * having these files or not
   *
   * @return whether if the root files exist or not
   */
  private boolean checkRootB() {
    for (final String path : rootFiles) {
      try {
        if (new File(path).exists()) {
          return true;
        }
      } catch (RuntimeException e) {
        logger.log(
            SentryLevel.ERROR, e, "Error when trying to check if root file %s exists.", path);
      }
    }
    return false;
  }

  /**
   * this will check if SU(Super User) exist or not
   *
   * @return whether su exists or not
   */
  private boolean checkRootC() {
    Process p = null;
    final String[] su = {"/system/xbin/which", "su"};

    try {
      p = runtime.exec(su);

      try (final BufferedReader reader =
          new BufferedReader(new InputStreamReader(p.getInputStream(), UTF_8))) {
        return reader.readLine() != null;
      }
    } catch (IOException e) {
      logger.log(SentryLevel.DEBUG, "SU isn't found on this Device.");
    } catch (Throwable e) {
      logger.log(SentryLevel.DEBUG, "Error when trying to check if SU exists.", e);
    } finally {
      if (p != null) {
        p.destroy();
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
