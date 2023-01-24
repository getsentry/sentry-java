package io.sentry.android.core.internal.util;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.os.Build;
import io.sentry.NoOpLogger;
import io.sentry.android.core.BuildInfoProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ContentProviderSecurityChecker {

  private final @NotNull BuildInfoProvider buildInfoProvider;

  public ContentProviderSecurityChecker() {
    this(new BuildInfoProvider(NoOpLogger.getInstance()));
  }

  public ContentProviderSecurityChecker(final @NotNull BuildInfoProvider buildInfoProvider) {
    this.buildInfoProvider = buildInfoProvider;
  }

  /**
   * Protects against "Privilege Escalation via Content Provider" (CVE-2018-9492).
   *
   * <p>Throws a SecurityException if the security check is breached.
   *
   * <p>See https://www.cvedetails.com/cve/CVE-2018-9492/ and
   * https://github.com/getsentry/sentry-java/issues/2460
   *
   * <p>Call this function in the {@link ContentProvider}'s implementations of the abstract
   * functions; query, insert, update, and delete.
   *
   * <p>This should be invoked regardless of whether there is data to read/write or not. The attack
   * is not contained to the specific provider but rather the entire system.
   *
   * <p>This blocks the attacker by only allowing the app itself (not other apps) to interact with
   * the ContentProvider. If the ContentProvider needs to be able to interact with other trusted
   * apps, then this function or class should be refactored to accommodate that.
   *
   * <p>The vulnerability is specific to un-patched versions of Android 8 and 9 (API 26 to 28).
   * Therefore, this security check is limited to those versions to mitigate risk of regression.
   */
  @SuppressLint("NewApi")
  public void checkPrivilegeEscalation(@NotNull ContentProvider contentProvider) {
    final int sdkVersion = buildInfoProvider.getSdkInfoVersion();
    if (sdkVersion >= Build.VERSION_CODES.O && sdkVersion <= Build.VERSION_CODES.P) {

      String callingPackage = contentProvider.getCallingPackage();
      String appPackage = contentProvider.getContext().getPackageName();
      if (callingPackage != null && callingPackage.equals(appPackage)) {
        return;
      }
      throw new SecurityException("Provider does not allow for granting of Uri permissions");
    }
  }
}
