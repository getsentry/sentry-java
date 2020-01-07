package io.sentry.android.core.util;

import android.Manifest;
import android.content.Context;
import android.net.ConnectivityManager;
import io.sentry.core.ILogger;
import io.sentry.core.SentryLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ConnectivityChecker {

  private ConnectivityChecker() {}

  /**
   * Check whether the application has internet access at a point in time.
   *
   * @return true if the application has internet access
   */
  public static @Nullable Boolean isConnected(@NotNull Context context, @NotNull ILogger logger) {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      logger.log(SentryLevel.INFO, "ConnectivityManager is null and cannot check network status");
      return null;
    }
    return getActiveNetworkInfo(context, connectivityManager, logger);
    // getActiveNetworkInfo might return null if VPN doesn't specify its
    // underlying network

    // when min. API 24, use:
    // connectivityManager.registerDefaultNetworkCallback(...)
  }

  @SuppressWarnings({"deprecation", "MissingPermission"})
  private static Boolean getActiveNetworkInfo(
      Context context, ConnectivityManager connectivityManager, ILogger logger) {
    if (!Permissions.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
      logger.log(SentryLevel.INFO, "No permission (ACCESS_NETWORK_STATE) to check network status.");
      return null;
    }

    // do not import class or deprecation lint will throw
    android.net.NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();

    if (activeNetwork != null) {
      return activeNetwork.isConnected();
    }
    logger.log(SentryLevel.INFO, "NetworkInfo is null and cannot check network status");
    return null;
  }
}
