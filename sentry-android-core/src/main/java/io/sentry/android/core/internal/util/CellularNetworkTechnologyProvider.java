package io.sentry.android.core.internal.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import io.sentry.ILogger;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.android.core.ContextUtils;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports the generation of the cellular network technology the device currently uses for data, for
 * example {@code 4g} or {@code 5g}.
 *
 * <p>From Android 12 (API 31) the value comes from {@link TelephonyCallback.DisplayInfoListener},
 * which requires no permission and reports what the device shows to the user. On older versions it
 * is read from {@link TelephonyManager#getDataNetworkType()}, which the SDK only calls when the
 * hosting app already holds one of the required permissions. The SDK never declares those
 * permissions itself, so apps without them simply get no technology reported.
 */
@ApiStatus.Internal
public final class CellularNetworkTechnologyProvider {

  public static final @NotNull String GENERATION_2G = "2g";
  public static final @NotNull String GENERATION_3G = "3g";
  public static final @NotNull String GENERATION_4G = "4g";
  public static final @NotNull String GENERATION_5G = "5g";

  private final @NotNull Context context;
  private final @NotNull ILogger logger;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull Executor executor;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  /**
   * Set from {@link TelephonyCallback.DisplayInfoListener} on API 31 and above. Declared as {@link
   * Object} so that loading this class on older devices never has to resolve a type that does not
   * exist there.
   */
  private volatile @Nullable Object displayInfoCallback;

  private volatile @Nullable String displayInfoTechnology;

  public CellularNetworkTechnologyProvider(
      final @NotNull Context context,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull Executor executor) {
    this.context = ContextUtils.getApplicationContext(context);
    this.logger = logger;
    this.buildInfoProvider = buildInfoProvider;
    this.executor = executor;
  }

  /**
   * The generation of the cellular network technology currently used for data, or {@code null} when
   * it is unknown.
   */
  public @Nullable String getCellularNetworkTechnology() {
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.S) {
      return displayInfoTechnology;
    }
    return getDataNetworkTechnology();
  }

  /**
   * Starts listening for display info changes on API 31 and above. Does nothing on older versions,
   * where the technology is read on demand instead.
   */
  @SuppressLint("NewApi")
  public void register() {
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.S) {
      return;
    }
    // Checking and storing the callback has to be atomic, otherwise concurrent callers can each
    // register a listener while only the last one is kept and can ever be unregistered.
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (displayInfoCallback != null) {
        return;
      }
      final @Nullable TelephonyManager telephonyManager = getTelephonyManager();
      if (telephonyManager == null) {
        return;
      }
      try {
        final @NotNull DisplayInfoCallback callback = new DisplayInfoCallback(this);
        telephonyManager.registerTelephonyCallback(executor, callback);
        displayInfoCallback = callback;
        logger.log(SentryLevel.DEBUG, "Started listening for cellular network technology changes.");
      } catch (SecurityException | IllegalStateException | UnsupportedOperationException e) {
        // Devices without telephony support and processes that are not allowed to listen throw
        // here, and the technology is optional data.
        logger.log(
            SentryLevel.INFO, "Could not listen for cellular network technology changes.", e);
      }
    }
  }

  /**
   * Stops listening for display info changes.
   *
   * <p>The last reported technology is kept. Monitoring stops when the app goes to the background,
   * and from API 31 on the listener is the only source, so discarding it would leave every
   * background event without a technology even though the connection type still resolves. It is
   * only ever paired with a connection that is cellular at the time the event is captured.
   */
  @SuppressLint("NewApi")
  public void unregister() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @Nullable Object callback = displayInfoCallback;
      if (callback == null) {
        return;
      }
      final @Nullable TelephonyManager telephonyManager = getTelephonyManager();
      if (telephonyManager == null) {
        // Keep the reference so a later unregister can still reach the registered callback.
        return;
      }
      try {
        telephonyManager.unregisterTelephonyCallback((TelephonyCallback) callback);
        displayInfoCallback = null;
        logger.log(SentryLevel.DEBUG, "Stopped listening for cellular network technology changes.");
      } catch (SecurityException | IllegalStateException | UnsupportedOperationException e) {
        logger.log(
            SentryLevel.INFO,
            "Could not stop listening for cellular network technology changes.",
            e);
      }
    }
  }

  @SuppressLint({"MissingPermission", "InlinedApi", "NewApi"})
  private @Nullable String getDataNetworkTechnology() {
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.N) {
      // getDataNetworkType was added in API 24.
      return null;
    }
    // The SDK doesn't declare these permissions, so the technology is only available to apps that
    // already request one of them. READ_BASIC_PHONE_STATE only exists from API 31 on, where the
    // display info listener is used instead, but checking it is harmless.
    if (!Permissions.hasPermission(context, Manifest.permission.READ_PHONE_STATE)
        && !Permissions.hasPermission(context, Manifest.permission.READ_BASIC_PHONE_STATE)) {
      return null;
    }
    final @Nullable TelephonyManager telephonyManager = getTelephonyManager();
    if (telephonyManager == null) {
      return null;
    }
    try {
      return networkTypeToGeneration(telephonyManager.getDataNetworkType());
    } catch (SecurityException | UnsupportedOperationException e) {
      // The permission can be revoked between the check above and the call, and devices without
      // telephony hardware throw UnsupportedOperationException from API 36 on.
      logger.log(SentryLevel.INFO, "Could not retrieve the cellular network technology.", e);
      return null;
    }
  }

  private @Nullable TelephonyManager getTelephonyManager() {
    final @Nullable Object service = context.getSystemService(Context.TELEPHONY_SERVICE);
    if (!(service instanceof TelephonyManager)) {
      logger.log(SentryLevel.INFO, "TelephonyManager is not available.");
      return null;
    }
    return (TelephonyManager) service;
  }

  /**
   * Maps a {@link TelephonyDisplayInfo} to a generation, preferring the override type so that a
   * device showing 5G to the user is also reported as 5G.
   */
  @RequiresApi(api = Build.VERSION_CODES.S)
  @SuppressWarnings("deprecation")
  static @Nullable String toGeneration(final @NotNull TelephonyDisplayInfo displayInfo) {
    switch (displayInfo.getOverrideNetworkType()) {
      case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA:
      case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED:
      // Deprecated in favour of NR_ADVANCED, but still reported on Android 11. Its underlying
      // network type is LTE, so without this case a 5G mmWave connection reports 4g.
      case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE:
        return GENERATION_5G;
      case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA:
      case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO:
        return GENERATION_4G;
      default:
        return networkTypeToGeneration(displayInfo.getNetworkType());
    }
  }

  /** Maps a {@code TelephonyManager.NETWORK_TYPE_*} constant to a generation. */
  @SuppressWarnings("deprecation")
  static @Nullable String networkTypeToGeneration(final int networkType) {
    switch (networkType) {
      case TelephonyManager.NETWORK_TYPE_GPRS:
      case TelephonyManager.NETWORK_TYPE_EDGE:
      case TelephonyManager.NETWORK_TYPE_CDMA:
      case TelephonyManager.NETWORK_TYPE_1xRTT:
      case TelephonyManager.NETWORK_TYPE_IDEN:
      case TelephonyManager.NETWORK_TYPE_GSM:
        return GENERATION_2G;
      case TelephonyManager.NETWORK_TYPE_UMTS:
      case TelephonyManager.NETWORK_TYPE_EVDO_0:
      case TelephonyManager.NETWORK_TYPE_EVDO_A:
      case TelephonyManager.NETWORK_TYPE_EVDO_B:
      case TelephonyManager.NETWORK_TYPE_EHRPD:
      case TelephonyManager.NETWORK_TYPE_HSDPA:
      case TelephonyManager.NETWORK_TYPE_HSUPA:
      case TelephonyManager.NETWORK_TYPE_HSPA:
      case TelephonyManager.NETWORK_TYPE_HSPAP:
      case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
        return GENERATION_3G;
      case TelephonyManager.NETWORK_TYPE_LTE:
      case TelephonyManager.NETWORK_TYPE_IWLAN:
        return GENERATION_4G;
      case TelephonyManager.NETWORK_TYPE_NR:
        return GENERATION_5G;
      default:
        return null;
    }
  }

  /** Caches the display info so that reading the technology never blocks on telephony. */
  @RequiresApi(api = Build.VERSION_CODES.S)
  private static final class DisplayInfoCallback extends TelephonyCallback
      implements TelephonyCallback.DisplayInfoListener {

    private final @NotNull CellularNetworkTechnologyProvider provider;

    DisplayInfoCallback(final @NotNull CellularNetworkTechnologyProvider provider) {
      this.provider = provider;
    }

    @Override
    public void onDisplayInfoChanged(final @NonNull TelephonyDisplayInfo telephonyDisplayInfo) {
      provider.displayInfoTechnology = toGeneration(telephonyDisplayInfo);
    }
  }
}
