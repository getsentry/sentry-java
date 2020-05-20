package io.sentry.android.core;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.os.BatteryManager.EXTRA_TEMPERATURE;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import io.sentry.android.core.util.ConnectivityChecker;
import io.sentry.android.core.util.DeviceOrientations;
import io.sentry.core.DateUtils;
import io.sentry.core.EventProcessor;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.protocol.App;
import io.sentry.core.protocol.DebugImage;
import io.sentry.core.protocol.DebugMeta;
import io.sentry.core.protocol.Device;
import io.sentry.core.protocol.OperatingSystem;
import io.sentry.core.protocol.SdkVersion;
import io.sentry.core.protocol.SentryThread;
import io.sentry.core.protocol.User;
import io.sentry.core.util.ApplyScopeUtils;
import io.sentry.core.util.Objects;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class DefaultAndroidEventProcessor implements EventProcessor {

  @TestOnly static final String PROGUARD_UUID = "proGuardUuids";
  @TestOnly static final String ROOTED = "rooted";
  @TestOnly static final String ANDROID_ID = "androidId";
  @TestOnly static final String KERNEL_VERSION = "kernelVersion";
  @TestOnly static final String EMULATOR = "emulator";

  // it could also be a parameter and get from Sentry.init(...)
  private static final Date appStartTime = DateUtils.getCurrentDateTime();

  @TestOnly final Context context;

  private final SentryOptions options;

  @TestOnly final Future<Map<String, Object>> contextData;

  private final @NotNull IBuildInfoProvider buildInfoProvider;

  public DefaultAndroidEventProcessor(
      final @NotNull Context context,
      final @NotNull SentryOptions options,
      final @NotNull IBuildInfoProvider buildInfoProvider) {
    this.context = Objects.requireNonNull(context, "The application context is required.");
    this.options = Objects.requireNonNull(options, "The SentryOptions is required.");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "The BuildInfoProvider is required.");

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    // dont ref. to method reference, theres a bug on it
    contextData = executorService.submit(() -> loadContextData());

    executorService.shutdown();
  }

  private @NotNull Map<String, Object> loadContextData() {
    Map<String, Object> map = new HashMap<>();
    String[] proGuardUuids = getProGuardUuids();
    if (proGuardUuids != null) {
      map.put(PROGUARD_UUID, proGuardUuids);
    }

    map.put(ROOTED, isRooted());

    String androidId = getAndroidId();
    if (androidId != null) {
      map.put(ANDROID_ID, androidId);
    }

    String kernelVersion = getKernelVersion();
    if (kernelVersion != null) {
      map.put(KERNEL_VERSION, kernelVersion);
    }

    // its not IO, but it has been cached in the old version as well
    map.put(EMULATOR, isEmulator());

    return map;
  }

  @Override
  public @NotNull SentryEvent process(
      final @NotNull SentryEvent event, final @Nullable Object hint) {
    if (ApplyScopeUtils.shouldApplyScopeData(hint)) {
      processNonCachedEvent(event);
    } else {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Event was cached so not applying data relevant to the current app execution/version: %s",
              event.getEventId());
    }

    if (event.getContexts().getDevice() == null) {
      event.getContexts().setDevice(getDevice());
    }
    if (event.getContexts().getOperatingSystem() == null) {
      event.getContexts().setOperatingSystem(getOperatingSystem());
    }

    return event;
  }

  // Data to be applied to events that was created in the running process
  private void processNonCachedEvent(final @NotNull SentryEvent event) {
    if (event.getUser() == null) {
      event.setUser(getUser());
    }

    App app = event.getContexts().getApp();
    if (app == null) {
      app = new App();
    }
    setAppExtras(app);

    if (event.getDebugMeta() == null) {
      event.setDebugMeta(getDebugMeta());
    }
    if (event.getSdk() == null) {
      event.setSdk(getSdkVersion());
    }

    PackageInfo packageInfo = ContextUtils.getPackageInfo(context, options.getLogger());
    if (packageInfo != null) {
      String versionCode = ContextUtils.getVersionCode(packageInfo);

      if (event.getDist() == null) {
        event.setDist(versionCode);
      }
      setAppPackageInfo(app, packageInfo);
    }

    event.getContexts().setApp(app);

    if (event.getThreads() != null) {
      for (SentryThread thread : event.getThreads()) {
        thread.setCurrent(Looper.getMainLooper().getThread().getId() == thread.getId());
      }
    }
  }

  private @Nullable List<DebugImage> getDebugImages() {
    String[] uuids = null;
    try {
      Object proGuardUuids = contextData.get().get(PROGUARD_UUID);
      if (proGuardUuids != null) {
        uuids = (String[]) proGuardUuids;
      }
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting proGuardUuids.", e);
      return null;
    }

    if (uuids == null || uuids.length == 0) {
      return null;
    }

    List<DebugImage> images = new ArrayList<>();

    for (String item : uuids) {
      DebugImage debugImage = new DebugImage();
      debugImage.setType("proguard");
      debugImage.setUuid(item);
      images.add(debugImage);
    }

    return images;
  }

  private @Nullable DebugMeta getDebugMeta() {
    List<DebugImage> debugImages = getDebugImages();

    if (debugImages == null) {
      return null;
    }

    DebugMeta debugMeta = new DebugMeta();
    debugMeta.setImages(debugImages);
    return debugMeta;
  }

  private void setAppExtras(final @NotNull App app) {
    app.setAppName(getApplicationName());
    app.setAppStartTime(appStartTime);
  }

  private @NotNull SdkVersion getSdkVersion() {
    SdkVersion sdkVersion = new SdkVersion();

    sdkVersion.setName("sentry.java.android");
    String version = BuildConfig.VERSION_NAME;
    sdkVersion.setVersion(version);
    sdkVersion.addPackage("maven:sentry-core", version);
    sdkVersion.addPackage("maven:sentry-android-core", version);
    if (options.isEnableNdk()) {
      sdkVersion.addPackage("maven:sentry-android-ndk", version);
    }

    return sdkVersion;
  }

  @SuppressWarnings("deprecation")
  private @NotNull String getAbi() {
    return Build.CPU_ABI;
  }

  @SuppressWarnings("deprecation")
  private @NotNull String getAbi2() {
    return Build.CPU_ABI2;
  }

  @SuppressWarnings({"ObsoleteSdkInt", "deprecation"})
  private void setArchitectures(final @NotNull Device device) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      String[] supportedAbis = Build.SUPPORTED_ABIS;
      device.setArch(supportedAbis[0]);
      device.setArchs(supportedAbis);
    } else {
      String[] supportedAbis = {getAbi(), getAbi2()};
      device.setArch(supportedAbis[0]);
      device.setArchs(supportedAbis);
      // we were not checking CPU_ABI2, but I've added to the list now
    }
  }

  @SuppressWarnings("ObsoleteSdkInt")
  private @NotNull Long getMemorySize(final @NotNull ActivityManager.MemoryInfo memInfo) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      return memInfo.totalMem;
    }
    // using Runtime as a fallback
    return java.lang.Runtime.getRuntime().totalMemory(); // JVM in bytes too
  }

  // we can get some inspiration here
  // https://github.com/flutter/plugins/blob/master/packages/device_info/android/src/main/java/io/flutter/plugins/deviceinfo/DeviceInfoPlugin.java
  private @NotNull Device getDevice() {
    // TODO: missing usable memory

    Device device = new Device();
    device.setName(getDeviceName());
    device.setManufacturer(Build.MANUFACTURER);
    device.setBrand(Build.BRAND);
    device.setFamily(getFamily());
    device.setModel(Build.MODEL);
    device.setModelId(Build.ID);
    setArchitectures(device);

    Intent batteryIntent = getBatteryIntent();
    if (batteryIntent != null) {
      device.setBatteryLevel(getBatteryLevel(batteryIntent));
      device.setCharging(isCharging(batteryIntent));
      device.setBatteryTemperature(getBatteryTemperature(batteryIntent));
    }
    Boolean connected;
    switch (ConnectivityChecker.getConnectionStatus(context, options.getLogger())) {
      case NOT_CONNECTED:
        connected = false;
        break;
      case CONNECTED:
        connected = true;
        break;
      default:
        connected = null;
    }
    device.setOnline(connected);
    device.setOrientation(getOrientation());

    try {
      Object emulator = contextData.get().get(EMULATOR);
      if (emulator != null) {
        device.setSimulator((Boolean) emulator);
      }
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting emulator.", e);
    }

    ActivityManager.MemoryInfo memInfo = getMemInfo();
    if (memInfo != null) {
      // in bytes
      device.setMemorySize(getMemorySize(memInfo));
      device.setFreeMemory(memInfo.availMem);
      device.setLowMemory(memInfo.lowMemory);
      // there are runtime.totalMemory() and runtime.freeMemory(), but I kept the same for
      // compatibility
    }

    // this way of getting the size of storage might be problematic for storages bigger than 2GB
    // check the use of https://developer.android.com/reference/java/io/File.html#getFreeSpace%28%29
    File internalStorageFile = context.getExternalFilesDir(null);
    if (internalStorageFile != null) {
      StatFs internalStorageStat = new StatFs(internalStorageFile.getPath());
      device.setStorageSize(getTotalInternalStorage(internalStorageStat));
      device.setFreeStorage(getUnusedInternalStorage(internalStorageStat));
    }

    StatFs externalStorageStat = getExternalStorageStat(internalStorageFile);
    if (externalStorageStat != null) {
      device.setExternalStorageSize(getTotalExternalStorage(externalStorageStat));
      device.setExternalFreeStorage(getUnusedExternalStorage(externalStorageStat));
    }

    DisplayMetrics displayMetrics = getDisplayMetrics();
    if (displayMetrics != null) {
      setScreenResolution(device, displayMetrics);
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
    if (device.getLanguage() == null) {
      device.setLanguage(Locale.getDefault().toString()); // eg en_US
    }
    if (device.getConnectionType() == null) {
      // wifi, ethernet or cellular, null if none
      device.setConnectionType(
          ConnectivityChecker.getConnectionType(context, options.getLogger(), buildInfoProvider));
    }

    return device;
  }

  @SuppressWarnings("ObsoleteSdkInt")
  private @Nullable String getDeviceName() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return Settings.Global.getString(context.getContentResolver(), "device_name");
    } else {
      return null;
    }
  }

  @SuppressWarnings("deprecation")
  private void setScreenResolution(
      final @NotNull Device device, final @NotNull DisplayMetrics displayMetrics) {
    device.setScreenResolution(getResolution(displayMetrics));
  }

  private TimeZone getTimeZone() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      LocaleList locales = context.getResources().getConfiguration().getLocales();
      if (!locales.isEmpty()) {
        Locale locale = locales.get(0);
        return Calendar.getInstance(locale).getTimeZone();
      }
    }
    return Calendar.getInstance().getTimeZone();
  }

  private @NotNull Date getBootTime() {
    // if user changes time, will give a wrong answer, consider ACTION_TIME_CHANGED
    return DateUtils.getDateTime(
        new Date(System.currentTimeMillis() - SystemClock.elapsedRealtime()));
  }

  private @NotNull String getResolution(final @NotNull DisplayMetrics displayMetrics) {
    int largestSide = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
    int smallestSide = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
    return largestSide + "x" + smallestSide;
  }

  /**
   * Get MemoryInfo object representing the memory state of the application.
   *
   * @return MemoryInfo object representing the memory state of the application
   */
  private @Nullable ActivityManager.MemoryInfo getMemInfo() {
    try {
      ActivityManager actManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
      ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
      if (actManager != null) {
        actManager.getMemoryInfo(memInfo);
        return memInfo;
      }
      options.getLogger().log(SentryLevel.INFO, "Error getting MemoryInfo.");
      return null;
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting MemoryInfo.", e);
      return null;
    }
  }

  private @Nullable Intent getBatteryIntent() {
    return context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  }

  /**
   * Fake the device family by using the first word in the Build.MODEL. Works well in most cases...
   * "Nexus 6P" -> "Nexus", "Galaxy S7" -> "Galaxy".
   *
   * @return family name of the device, as best we can tell
   */
  private @Nullable String getFamily() {
    try {
      return Build.MODEL.split(" ", -1)[0];
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting device family.", e);
      return null;
    }
  }

  /**
   * Get the device's current battery level (as a percentage of total).
   *
   * @return the device's current battery level (as a percentage of total), or null if unknown
   */
  private @Nullable Float getBatteryLevel(final @NotNull Intent batteryIntent) {
    try {
      int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
      int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

      if (level == -1 || scale == -1) {
        return null;
      }

      float percentMultiplier = 100.0f;

      return ((float) level / (float) scale) * percentMultiplier;
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting device battery level.", e);
      return null;
    }
  }

  /**
   * Checks whether or not the device is currently plugged in and charging, or null if unknown.
   *
   * @return whether or not the device is currently plugged in and charging, or null if unknown
   */
  private @Nullable Boolean isCharging(final @NotNull Intent batteryIntent) {
    try {
      int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
      return plugged == BatteryManager.BATTERY_PLUGGED_AC
          || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting device charging state.", e);
      return null;
    }
  }

  private @Nullable Float getBatteryTemperature(final @NotNull Intent batteryIntent) {
    try {
      int temperature = batteryIntent.getIntExtra(EXTRA_TEMPERATURE, -1);
      if (temperature != -1) {
        return ((float) temperature) / 10; // celsius
      }
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting battery temperature.", e);
    }
    return null;
  }

  /**
   * Get the device's current screen orientation.
   *
   * @return the device's current screen orientation, or null if unknown
   */
  @SuppressWarnings("deprecation")
  private @Nullable Device.DeviceOrientation getOrientation() {
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
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting device orientation.", e);
    }
    return deviceOrientation;
  }

  /**
   * Check whether the application is running in an emulator.
   * https://github.com/flutter/plugins/blob/master/packages/device_info/android/src/main/java/io/flutter/plugins/deviceinfo/DeviceInfoPlugin.java#L105
   *
   * @return true if the application is running in an emulator, false otherwise
   */
  private @Nullable Boolean isEmulator() {
    try {
      return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
          || Build.FINGERPRINT.startsWith("generic")
          || Build.FINGERPRINT.startsWith("unknown")
          || Build.HARDWARE.contains("goldfish")
          || Build.HARDWARE.contains("ranchu")
          || Build.MODEL.contains("google_sdk")
          || Build.MODEL.contains("Emulator")
          || Build.MODEL.contains("Android SDK built for x86")
          || Build.MANUFACTURER.contains("Genymotion")
          || Build.PRODUCT.contains("sdk_google")
          || Build.PRODUCT.contains("google_sdk")
          || Build.PRODUCT.contains("sdk")
          || Build.PRODUCT.contains("sdk_x86")
          || Build.PRODUCT.contains("vbox86p")
          || Build.PRODUCT.contains("emulator")
          || Build.PRODUCT.contains("simulator");
    } catch (Exception e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Error checking whether application is running in an emulator.",
              e);
      return null;
    }
  }

  /**
   * Get the total amount of internal storage, in bytes.
   *
   * @return the total amount of internal storage, in bytes
   */
  private @Nullable Long getTotalInternalStorage(final @NotNull StatFs stat) {
    try {
      long blockSize = getBlockSizeLong(stat);
      long totalBlocks = getBlockCountLong(stat);
      return totalBlocks * blockSize;
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting total internal storage amount.", e);
      return null;
    }
  }

  @SuppressWarnings("ObsoleteSdkInt")
  private long getBlockSizeLong(final @NotNull StatFs stat) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      return stat.getBlockSizeLong();
    }
    return getBlockSizeDep(stat);
  }

  @SuppressWarnings("deprecation")
  private int getBlockSizeDep(final @NotNull StatFs stat) {
    return stat.getBlockSize();
  }

  @SuppressWarnings("ObsoleteSdkInt")
  private long getBlockCountLong(final @NotNull StatFs stat) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      return stat.getBlockCountLong();
    }
    return getBlockCountDep(stat);
  }

  @SuppressWarnings("deprecation")
  private int getBlockCountDep(final @NotNull StatFs stat) {
    return stat.getBlockCount();
  }

  @SuppressWarnings("ObsoleteSdkInt")
  private long getAvailableBlocksLong(final @NotNull StatFs stat) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      return stat.getAvailableBlocksLong();
    }
    return getAvailableBlocksDep(stat);
  }

  @SuppressWarnings("deprecation")
  private int getAvailableBlocksDep(final @NotNull StatFs stat) {
    return stat.getAvailableBlocks();
  }

  /**
   * Get the unused amount of internal storage, in bytes.
   *
   * @return the unused amount of internal storage, in bytes
   */
  private @Nullable Long getUnusedInternalStorage(final @NotNull StatFs stat) {
    try {
      long blockSize = getBlockSizeLong(stat);
      long availableBlocks = getAvailableBlocksLong(stat);
      return availableBlocks * blockSize;
    } catch (Exception e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Error getting unused internal storage amount.", e);
      return null;
    }
  }

  private @Nullable StatFs getExternalStorageStat(final @Nullable File internalStorage) {
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

  @SuppressWarnings("ObsoleteSdkInt")
  private @Nullable File[] getExternalFilesDirs() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return context.getExternalFilesDirs(null);
    } else {
      File single = context.getExternalFilesDir(null);
      if (single != null) {
        return new File[] {single};
      }
    }
    return null;
  }

  private @Nullable File getExternalStorageDep(final @Nullable File internalStorage) {
    File[] externalFilesDirs = getExternalFilesDirs();

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
  private @Nullable Long getTotalExternalStorage(final @NotNull StatFs stat) {
    try {
      long blockSize = getBlockSizeLong(stat);
      long totalBlocks = getBlockCountLong(stat);
      return totalBlocks * blockSize;
    } catch (Exception e) {
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
  private @Nullable Long getUnusedExternalStorage(final @NotNull StatFs stat) {
    try {
      long blockSize = getBlockSizeLong(stat);
      long availableBlocks = getAvailableBlocksLong(stat);
      return availableBlocks * blockSize;
    } catch (Exception e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Error getting unused external storage amount.", e);
      return null;
    }
  }

  /**
   * Get the DisplayMetrics object for the current application.
   *
   * @return the DisplayMetrics object for the current application
   */
  private @Nullable DisplayMetrics getDisplayMetrics() {
    try {
      return context.getResources().getDisplayMetrics();
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting DisplayMetrics.", e);
      return null;
    }
  }

  private @NotNull OperatingSystem getOperatingSystem() {
    OperatingSystem os = new OperatingSystem();
    os.setName("Android");
    os.setVersion(Build.VERSION.RELEASE);
    os.setBuild(Build.DISPLAY);

    try {
      Object kernelVersion = contextData.get().get(KERNEL_VERSION);
      if (kernelVersion != null) {
        os.setKernelVersion((String) kernelVersion);
      }

      Object rooted = contextData.get().get(ROOTED);
      if (rooted != null) {
        os.setRooted((Boolean) rooted);
      }
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting OperatingSystem.", e);
    }

    return os;
  }

  private void setAppPackageInfo(final @NotNull App app, final @NotNull PackageInfo packageInfo) {
    app.setAppIdentifier(packageInfo.packageName);
    app.setAppVersion(packageInfo.versionName);
    app.setAppBuild(ContextUtils.getVersionCode(packageInfo));
  }

  /**
   * Get the device's current kernel version, as a string. Attempts to read /proc/version, and falls
   * back to the 'os.version' System Property.
   *
   * @return the device's current kernel version, as a string
   */
  @SuppressWarnings("DefaultCharset")
  private @Nullable String getKernelVersion() {
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
      options.getLogger().log(SentryLevel.ERROR, errorMsg, e);
    }

    return defaultVersion;
  }

  /**
   * Attempt to discover if this device is currently rooted. From:
   * https://stackoverflow.com/questions/1101380/determine-if-running-on-a-rooted-device
   *
   * @return true if heuristics show the device is probably rooted, otherwise false
   */
  private boolean isRooted() {
    // we could get some inspiration from https://github.com/scottyab/rootbeer
    if (Build.TAGS != null && Build.TAGS.contains("test-keys")) {
      return true;
    }

    String[] probableRootPaths = {
      "/data/local/bin/su",
      "/data/local/su",
      "/data/local/xbin/su",
      "/sbin/su",
      "/su/bin",
      "/su/bin/su",
      "/system/app/SuperSU",
      "/system/app/SuperSU.apk",
      "/system/app/Superuser",
      "/system/app/Superuser.apk",
      "/system/bin/failsafe/su",
      "/system/bin/su",
      "/system/sd/xbin/su",
      "/system/xbin/daemonsu",
      "/system/xbin/su"
    };

    for (String probableRootPath : probableRootPaths) {
      try {
        if (new File(probableRootPath).exists()) {
          return true;
        }
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "Exception while attempting to detect whether the device is rooted",
                e);
      }
    }

    return false;
  }

  /**
   * Get the human-facing Application name.
   *
   * @return Application name
   */
  private @Nullable String getApplicationName() {
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
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting application name.", e);
    }

    return null;
  }

  public @NotNull User getUser() {
    User user = new User();
    user.setId(getDeviceId());

    return user;
  }

  private @Nullable String getDeviceId() {
    try {
      Object androidId = contextData.get().get(ANDROID_ID);

      if (androidId != null) {
        return (String) androidId;
      }
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting androidId.", e);
    }
    return null;
  }

  @SuppressWarnings("HardwareIds")
  private @Nullable String getAndroidId() {
    // Android 29 has changed and -> Avoid using hardware identifiers, find another way in the
    // future
    String androidId =
        Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

    //    https://android-developers.googleblog.com/2011/03/identifying-app-installations.html
    if (androidId == null
        || androidId.isEmpty()
        || androidId.toLowerCase(Locale.ROOT).contentEquals("9774d56d682e549c")) {
      try {
        androidId = Installation.id(context);
      } catch (RuntimeException e) {
        options.getLogger().log(SentryLevel.ERROR, "Could not generate device Id.", e);

        return null;
      }
    }

    return androidId;
  }

  private @Nullable String[] getProGuardUuids() {
    AssetManager assets = context.getAssets();
    try {
      String[] files = assets.list("");

      List<String> listFiles = Arrays.asList(files != null ? files : new String[0]);
      if (listFiles.contains("sentry-debug-meta.properties")) {
        try (InputStream is = assets.open("sentry-debug-meta.properties")) {
          Properties properties = new Properties();
          properties.load(is);

          String uuid = properties.getProperty("io.sentry.ProguardUuids");
          if (uuid != null && !uuid.isEmpty()) {
            return uuid.split("\\|", -1);
          }
          options
              .getLogger()
              .log(SentryLevel.INFO, "io.sentry.ProguardUuids property was not found.");
        } catch (IOException e) {
          options.getLogger().log(SentryLevel.ERROR, "Error getting Proguard UUIDs.", e);
        }
        options
            .getLogger()
            .log(SentryLevel.INFO, "io.sentry.ProguardUuids property was not found.");
      } else {
        options.getLogger().log(SentryLevel.INFO, "Proguard UUIDs file not found.");
      }
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.ERROR, "Error listing Proguard files.", e);
    }

    return null;
  }
}
