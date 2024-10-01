package io.sentry.android.core;

import static android.os.BatteryManager.EXTRA_TEMPERATURE;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import io.sentry.DateUtils;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.util.CpuInfoUtils;
import io.sentry.android.core.internal.util.DeviceOrientations;
import io.sentry.android.core.internal.util.RootChecker;
import io.sentry.protocol.Device;
import io.sentry.protocol.OperatingSystem;
import java.io.File;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class DeviceInfoUtil {

  @SuppressLint("StaticFieldLeak")
  private static volatile DeviceInfoUtil instance;

  private final @NotNull Context context;
  private final @NotNull SentryAndroidOptions options;
  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @Nullable Boolean isEmulator;
  private final @Nullable ContextUtils.SideLoadedInfo sideLoadedInfo;
  private final @NotNull OperatingSystem os;

  private final @Nullable Long totalMem;

  public DeviceInfoUtil(
      final @NotNull Context context, final @NotNull SentryAndroidOptions options) {
    this.context = context;
    this.options = options;
    this.buildInfoProvider = new BuildInfoProvider(options.getLogger());

    // these are potentially expense IO operations
    CpuInfoUtils.getInstance().readMaxFrequencies();
    os = retrieveOperatingSystemInformation();
    isEmulator = buildInfoProvider.isEmulator();
    sideLoadedInfo =
        ContextUtils.retrieveSideLoadedInfo(context, options.getLogger(), buildInfoProvider);
    final @Nullable ActivityManager.MemoryInfo memInfo =
        ContextUtils.getMemInfo(context, options.getLogger());
    if (memInfo != null) {
      totalMem = memInfo.totalMem;
    } else {
      totalMem = null;
    }
  }

  @NotNull
  public static DeviceInfoUtil getInstance(
      final @NotNull Context context, final @NotNull SentryAndroidOptions options) {
    if (instance == null) {
      synchronized (DeviceInfoUtil.class) {
        if (instance == null) {
          instance = new DeviceInfoUtil(ContextUtils.getApplicationContext(context), options);
        }
      }
    }
    return instance;
  }

  @TestOnly
  public static void resetInstance() {
    instance = null;
  }

  // we can get some inspiration here
  // https://github.com/flutter/plugins/blob/master/packages/device_info/android/src/main/java/io/flutter/plugins/deviceinfo/DeviceInfoPlugin.java
  @NotNull
  public Device collectDeviceInformation(
      final boolean collectDeviceIO, final boolean collectDynamicData) {
    // TODO: missing usable memory
    final @NotNull Device device = new Device();

    if (options.isSendDefaultPii()) {
      device.setName(ContextUtils.getDeviceName(context));
    }
    device.setManufacturer(Build.MANUFACTURER);
    device.setBrand(Build.BRAND);
    device.setFamily(ContextUtils.getFamily(options.getLogger()));
    device.setModel(Build.MODEL);
    device.setModelId(Build.ID);
    device.setArchs(ContextUtils.getArchitectures(buildInfoProvider));

    device.setOrientation(getOrientation());
    if (isEmulator != null) {
      device.setSimulator(isEmulator);
    }

    final @Nullable DisplayMetrics displayMetrics =
        ContextUtils.getDisplayMetrics(context, options.getLogger());
    if (displayMetrics != null) {
      device.setScreenWidthPixels(displayMetrics.widthPixels);
      device.setScreenHeightPixels(displayMetrics.heightPixels);
      device.setScreenDensity(displayMetrics.density);
      device.setScreenDpi(displayMetrics.densityDpi);
    }

    device.setBootTime(getBootTime());
    device.setTimezone(getTimeZone());

    if (device.getId() == null) {
      device.setId(getDeviceId());
    }

    final @NotNull Locale locale = Locale.getDefault();
    if (device.getLanguage() == null) {
      device.setLanguage(locale.getLanguage());
    }
    if (device.getLocale() == null) {
      device.setLocale(locale.toString()); // eg en_US
    }

    final @NotNull List<Integer> cpuFrequencies = CpuInfoUtils.getInstance().readMaxFrequencies();
    if (!cpuFrequencies.isEmpty()) {
      device.setProcessorFrequency(Collections.max(cpuFrequencies).doubleValue());
      device.setProcessorCount(cpuFrequencies.size());
    }

    device.setMemorySize(totalMem);

    // setting such values require IO hence we don't run for transactions
    if (collectDeviceIO && options.isCollectAdditionalContext()) {
      setDeviceIO(device, collectDynamicData);
    }

    return device;
  }

  @NotNull
  public OperatingSystem getOperatingSystem() {
    return os;
  }

  @NotNull
  protected OperatingSystem retrieveOperatingSystemInformation() {

    final OperatingSystem os = new OperatingSystem();
    os.setName("Android");
    os.setVersion(Build.VERSION.RELEASE);
    os.setBuild(Build.DISPLAY);

    final @Nullable String kernelVersion = ContextUtils.getKernelVersion(options.getLogger());
    if (kernelVersion != null) {
      os.setKernelVersion(kernelVersion);
    }

    if (options.isEnableRootCheck()) {
      final boolean rooted =
          new RootChecker(context, buildInfoProvider, options.getLogger()).isDeviceRooted();
      os.setRooted(rooted);
    }
    return os;
  }

  @Nullable
  public ContextUtils.SideLoadedInfo getSideLoadedInfo() {
    return sideLoadedInfo;
  }

  private void setDeviceIO(final @NotNull Device device, final boolean includeDynamicData) {
    final Intent batteryIntent = getBatteryIntent();
    if (batteryIntent != null) {
      device.setBatteryLevel(getBatteryLevel(batteryIntent, options));
      device.setCharging(isCharging(batteryIntent, options));
      device.setBatteryTemperature(getBatteryTemperature(batteryIntent));
    }

    Boolean connected;
    switch (options.getConnectionStatusProvider().getConnectionStatus()) {
      case DISCONNECTED:
        connected = false;
        break;
      case CONNECTED:
        connected = true;
        break;
      default:
        connected = null;
    }
    device.setOnline(connected);

    final @Nullable ActivityManager.MemoryInfo memInfo =
        ContextUtils.getMemInfo(context, options.getLogger());
    if (memInfo != null && includeDynamicData) {
      // in bytes
      device.setFreeMemory(memInfo.availMem);
      device.setLowMemory(memInfo.lowMemory);
    }

    // this way of getting the size of storage might be problematic for storages bigger than 2GB
    // check the use of
    // https://developer.android.com/reference/java/io/File.html#getFreeSpace%28%29
    final @Nullable File internalStorageFile = context.getExternalFilesDir(null);
    if (internalStorageFile != null) {
      StatFs internalStorageStat = new StatFs(internalStorageFile.getPath());
      device.setStorageSize(getTotalInternalStorage(internalStorageStat));
      device.setFreeStorage(getUnusedInternalStorage(internalStorageStat));
    }

    final @Nullable StatFs externalStorageStat = getExternalStorageStat(internalStorageFile);
    if (externalStorageStat != null) {
      device.setExternalStorageSize(getTotalExternalStorage(externalStorageStat));
      device.setExternalFreeStorage(getUnusedExternalStorage(externalStorageStat));
    }

    if (device.getConnectionType() == null) {
      // wifi, ethernet or cellular, null if none
      device.setConnectionType(options.getConnectionStatusProvider().getConnectionType());
    }
  }

  @SuppressWarnings("NewApi")
  @NotNull
  private TimeZone getTimeZone() {
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.N) {
      LocaleList locales = context.getResources().getConfiguration().getLocales();
      if (!locales.isEmpty()) {
        Locale locale = locales.get(0);
        return Calendar.getInstance(locale).getTimeZone();
      }
    }
    return Calendar.getInstance().getTimeZone();
  }

  @SuppressWarnings("JdkObsolete")
  @Nullable
  private Date getBootTime() {
    try {
      // if user changes the clock, will give a wrong answer, consider ACTION_TIME_CHANGED.
      // currentTimeMillis returns UTC already
      return DateUtils.getDateTime(System.currentTimeMillis() - SystemClock.elapsedRealtime());
    } catch (IllegalArgumentException e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Error getting the device's boot time.");
    }
    return null;
  }

  @Nullable
  private Intent getBatteryIntent() {
    return ContextUtils.registerReceiver(
        context, buildInfoProvider, null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  }

  /**
   * Get the device's current battery level (as a percentage of total).
   *
   * @return the device's current battery level (as a percentage of total), or null if unknown
   */
  @Nullable
  public static Float getBatteryLevel(
      final @NotNull Intent batteryIntent, final @NotNull SentryOptions options) {
    try {
      int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
      int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

      if (level == -1 || scale == -1) {
        return null;
      }

      float percentMultiplier = 100.0f;

      return ((float) level / (float) scale) * percentMultiplier;
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting device battery level.", e);
      return null;
    }
  }

  /**
   * Checks whether or not the device is currently plugged in and charging, or null if unknown.
   *
   * @return whether or not the device is currently plugged in and charging, or null if unknown
   */
  @Nullable
  public static Boolean isCharging(
      final @NotNull Intent batteryIntent, final @NotNull SentryOptions options) {
    try {
      int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
      return plugged == BatteryManager.BATTERY_PLUGGED_AC
          || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting device charging state.", e);
      return null;
    }
  }

  @Nullable
  private Float getBatteryTemperature(final @NotNull Intent batteryIntent) {
    try {
      int temperature = batteryIntent.getIntExtra(EXTRA_TEMPERATURE, -1);
      if (temperature != -1) {
        return ((float) temperature) / 10; // celsius
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting battery temperature.", e);
    }
    return null;
  }

  /**
   * Get the device's current screen orientation.
   *
   * @return the device's current screen orientation, or null if unknown
   */
  @Nullable
  private Device.DeviceOrientation getOrientation() {
    Device.DeviceOrientation deviceOrientation = null;
    try {
      deviceOrientation =
          DeviceOrientations.getOrientation(context.getResources().getConfiguration().orientation);
      if (deviceOrientation == null) {
        options
            .getLogger()
            .log(
                SentryLevel.INFO,
                "No device orientation available (ORIENTATION_SQUARE|ORIENTATION_UNDEFINED)");
        return null;
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting device orientation.", e);
    }
    return deviceOrientation;
  }

  /**
   * Get the total amount of internal storage, in bytes.
   *
   * @return the total amount of internal storage, in bytes
   */
  @Nullable
  private Long getTotalInternalStorage(final @NotNull StatFs stat) {
    try {
      long blockSize = stat.getBlockSizeLong();
      long totalBlocks = stat.getBlockCountLong();
      return totalBlocks * blockSize;
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting total internal storage amount.", e);
      return null;
    }
  }

  /**
   * Get the unused amount of internal storage, in bytes.
   *
   * @return the unused amount of internal storage, in bytes
   */
  @Nullable
  private Long getUnusedInternalStorage(final @NotNull StatFs stat) {
    try {
      long blockSize = stat.getBlockSizeLong();
      long availableBlocks = stat.getAvailableBlocksLong();
      return availableBlocks * blockSize;
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Error getting unused internal storage amount.", e);
      return null;
    }
  }

  @Nullable
  private StatFs getExternalStorageStat(final @Nullable File internalStorage) {
    if (!isExternalStorageMounted()) {
      File path = getExternalStorageDep(internalStorage);
      if (path != null) { // && path.canRead()) { canRead() will read return false
        return new StatFs(path.getPath());
      }
      options.getLogger().log(SentryLevel.INFO, "Not possible to read external files directory");
      return null;
    }
    options.getLogger().log(SentryLevel.INFO, "External storage is not mounted or emulated.");
    return null;
  }

  @Nullable
  private File getExternalStorageDep(final @Nullable File internalStorage) {
    final @Nullable File[] externalFilesDirs = context.getExternalFilesDirs(null);

    if (externalFilesDirs != null) {
      // return the 1st file which is not the emulated internal storage
      String internalStoragePath =
          internalStorage != null ? internalStorage.getAbsolutePath() : null;
      for (File file : externalFilesDirs) {
        // externalFilesDirs may contain null values :(
        if (file == null) {
          continue;
        }

        // return the 1st file if you cannot compare with the internal one
        if (internalStoragePath == null || internalStoragePath.isEmpty()) {
          return file;
        }
        // if we are looking to the same directory, let's check the next one or no external storage
        if (file.getAbsolutePath().contains(internalStoragePath)) {
          continue;
        }
        return file;
      }
    } else {
      options.getLogger().log(SentryLevel.INFO, "Not possible to read getExternalFilesDirs");
    }
    return null;
  }

  /**
   * Get the total amount of external storage, in bytes, or null if no external storage is mounted.
   *
   * @return the total amount of external storage, in bytes, or null if no external storage is
   *     mounted
   */
  @Nullable
  private Long getTotalExternalStorage(final @NotNull StatFs stat) {
    try {
      final long blockSize = stat.getBlockSizeLong();
      final long totalBlocks = stat.getBlockCountLong();
      return totalBlocks * blockSize;
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting total external storage amount.", e);
      return null;
    }
  }

  private boolean isExternalStorageMounted() {
    final String storageState = Environment.getExternalStorageState();
    return (Environment.MEDIA_MOUNTED.equals(storageState)
            || Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState))
        && !Environment.isExternalStorageEmulated();
  }

  /**
   * Get the unused amount of external storage, in bytes, or null if no external storage is mounted.
   *
   * @return the unused amount of external storage, in bytes, or null if no external storage is
   *     mounted
   */
  @Nullable
  private Long getUnusedExternalStorage(final @NotNull StatFs stat) {
    try {
      final long blockSize = stat.getBlockSizeLong();
      final long availableBlocks = stat.getAvailableBlocksLong();
      return availableBlocks * blockSize;
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Error getting unused external storage amount.", e);
      return null;
    }
  }

  @Nullable
  private String getDeviceId() {
    try {
      return Installation.id(context);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting installationId.", e);
    }
    return null;
  }
}
