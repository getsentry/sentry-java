package io.sentry.android.core;

import static android.content.Context.ACTIVITY_SERVICE;
import static io.sentry.core.ILogger.logIfNotNull;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import io.sentry.android.core.util.Permissions;
import io.sentry.core.DateUtils;
import io.sentry.core.EventProcessor;
import io.sentry.core.ILogger;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.hints.Cached;
import io.sentry.core.protocol.App;
import io.sentry.core.protocol.DebugImage;
import io.sentry.core.protocol.DebugMeta;
import io.sentry.core.protocol.Device;
import io.sentry.core.protocol.OperatingSystem;
import io.sentry.core.protocol.SdkVersion;
import io.sentry.core.protocol.User;
import io.sentry.core.util.Objects;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import org.jetbrains.annotations.Nullable;

final class DefaultAndroidEventProcessor implements EventProcessor {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");
  // it could also be a parameter and get from Sentry.init(...)
  private static final Date appStartTime = DateUtils.getCurrentDateTime();
  final Context context;
  private final SentryOptions options;

  public DefaultAndroidEventProcessor(Context context, SentryOptions options) {
    this.context =
        Objects.requireNonNull(
            context != null ? context.getApplicationContext() : null,
            "The application context is required.");
    this.options = Objects.requireNonNull(options, "The SentryOptions is required.");
  }

  @Override
  public SentryEvent process(SentryEvent event, @Nullable Object hint) {
    if (!(hint instanceof Cached)) {
      processNonCachedEvent(event);
    } else {
      logIfNotNull(
          options.getLogger(),
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
  private void processNonCachedEvent(SentryEvent event) {
    if (event.getUser() == null) {
      event.setUser(getUser());
    }
    setAppExtras(event);

    if (event.getDebugMeta() == null) {
      event.setDebugMeta(getDebugMeta());
    }
    if (event.getSdk() == null) {
      event.setSdk(getSdkVersion());
    }

    PackageInfo packageInfo = getPackageInfo();
    if (packageInfo != null) {
      if (event.getRelease() == null) {
        event.setRelease(packageInfo.packageName + "-" + packageInfo.versionName);
      }
      if (event.getDist() == null) {
        event.setDist(getVersionCode(packageInfo));
      }
      if (event.getContexts().getApp() == null) {
        event.getContexts().setApp(getApp(packageInfo));
      }
    }
  }

  private List<DebugImage> getDebugImages() {
    String[] uuids = getProGuardUuids();

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

  private DebugMeta getDebugMeta() {
    List<DebugImage> debugImages = getDebugImages();

    if (debugImages == null) {
      return null;
    }

    DebugMeta debugMeta = new DebugMeta();
    debugMeta.setImages(debugImages);
    return debugMeta;
  }

  private void setAppExtras(SentryEvent event) {
    App app = event.getContexts().getApp();
    if (event.getContexts().getApp() == null) {
      app = new App();
    }
    app.setAppName(getApplicationName());
    app.setAppStartTime(appStartTime);
  }

  private SdkVersion getSdkVersion() {
    SdkVersion sdkVersion = new SdkVersion();

    sdkVersion.setName("sentry.java.android");
    String version = BuildConfig.VERSION_NAME;
    sdkVersion.setVersion(version);
    sdkVersion.addPackage("sentry-core", version);
    sdkVersion.addPackage("sentry-android-core", version);
    if (options.isEnableNdk()) {
      sdkVersion.addPackage("sentry-android-ndk", version);
    }

    return sdkVersion;
  }

  private String getVersionCode(PackageInfo packageInfo) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return Long.toString(packageInfo.getLongVersionCode());
    }
    return getVersionCodeDep(packageInfo);
  }

  @SuppressWarnings("deprecation")
  private String getVersionCodeDep(PackageInfo packageInfo) {
    return Integer.toString(packageInfo.versionCode);
  }

  @SuppressWarnings("deprecation")
  private String getAbi() {
    return Build.CPU_ABI;
  }

  @SuppressWarnings("deprecation")
  private String getAbi2() {
    return Build.CPU_ABI2;
  }

  /**
   * Return the Application's PackageInfo if possible, or null.
   *
   * @return the Application's PackageInfo if possible, or null
   */
  private PackageInfo getPackageInfo() {
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting package info.", e);
      return null;
    }
  }

  @SuppressWarnings({"ObsoleteSdkInt", "deprecation"})
  private void setArchitectures(Device device) {
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
  private Long getMemorySize(ActivityManager.MemoryInfo memInfo) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      return memInfo.totalMem;
    }
    // using Runtime as a fallback
    return java.lang.Runtime.getRuntime().totalMemory(); // JVM in bytes too
  }

  // we can get some inspiration here
  // https://github.com/flutter/plugins/blob/master/packages/device_info/android/src/main/java/io/flutter/plugins/deviceinfo/DeviceInfoPlugin.java
  private Device getDevice() {
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
    }
    device.setOnline(isConnected());
    device.setOrientation(getOrientation());
    device.setSimulator(isEmulator());

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

    return device;
  }

  @SuppressWarnings("ObsoleteSdkInt")
  private String getDeviceName() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return Settings.Global.getString(context.getContentResolver(), "device_name");
    } else {
      return null;
    }
  }

  @SuppressWarnings("deprecation")
  private void setScreenResolution(Device device, DisplayMetrics displayMetrics) {
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

  private Date getBootTime() {
    // if user changes time, will give a wrong answer, consider ACTION_TIME_CHANGED
    return new Date(System.currentTimeMillis() - SystemClock.elapsedRealtime());
  }

  private String getResolution(DisplayMetrics displayMetrics) {
    int largestSide = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
    int smallestSide = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
    return largestSide + "x" + smallestSide;
  }

  /**
   * Get MemoryInfo object representing the memory state of the application.
   *
   * @return MemoryInfo object representing the memory state of the application
   */
  private ActivityManager.MemoryInfo getMemInfo() {
    try {
      ActivityManager actManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
      ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
      if (actManager != null) {
        actManager.getMemoryInfo(memInfo);
        return memInfo;
      }
      log(SentryLevel.INFO, "Error getting MemoryInfo.");
      return null;
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting MemoryInfo.", e);
      return null;
    }
  }

  private Intent getBatteryIntent() {
    return context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  }

  /**
   * Fake the device family by using the first word in the Build.MODEL. Works well in most cases...
   * "Nexus 6P" -> "Nexus", "Galaxy S7" -> "Galaxy".
   *
   * @return family name of the device, as best we can tell
   */
  private String getFamily() {
    try {
      return Build.MODEL.split(" ", -1)[0];
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting device family.", e);
      return null;
    }
  }

  /**
   * Get the device's current battery level (as a percentage of total).
   *
   * @return the device's current battery level (as a percentage of total), or null if unknown
   */
  private Float getBatteryLevel(Intent batteryIntent) {
    try {
      int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
      int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

      if (level == -1 || scale == -1) {
        return null;
      }

      float percentMultiplier = 100.0f;

      return ((float) level / (float) scale) * percentMultiplier;
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting device battery level.", e);
      return null;
    }
  }

  /**
   * Checks whether or not the device is currently plugged in and charging, or null if unknown.
   *
   * @return whether or not the device is currently plugged in and charging, or null if unknown
   */
  private Boolean isCharging(Intent batteryIntent) {
    try {
      int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
      return plugged == BatteryManager.BATTERY_PLUGGED_AC
          || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting device charging state.", e);
      return null;
    }
  }

  /**
   * Check whether the application has internet access at a point in time.
   *
   * @return true if the application has internet access
   */
  private Boolean isConnected() {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      log(SentryLevel.INFO, "ConnectivityManager is null and cannot check network status");
      return null;
    }
    return getActiveNetworkInfo(connectivityManager);
    // getActiveNetworkInfo might return null if VPN doesn't specify its
    // underlying network

    // when min. API 24, use:
    // connectivityManager.registerDefaultNetworkCallback(...)
  }

  @SuppressWarnings({"deprecation", "MissingPermission"})
  private Boolean getActiveNetworkInfo(ConnectivityManager connectivityManager) {
    if (!Permissions.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
      log(SentryLevel.INFO, "No permission (ACCESS_NETWORK_STATE) to check network status.");
      return null;
    }

    // do not import class or deprecation lint will throw
    android.net.NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();

    if (activeNetwork != null) {
      return activeNetwork.isConnected();
    }
    log(SentryLevel.INFO, "NetworkInfo is null and cannot check network status");
    return null;
  }

  /**
   * Get the device's current screen orientation.
   *
   * @return the device's current screen orientation, or null if unknown
   */
  private Device.DeviceOrientation getOrientation() {
    try {
      switch (context.getResources().getConfiguration().orientation) {
        case Configuration.ORIENTATION_LANDSCAPE:
          return Device.DeviceOrientation.LANDSCAPE;
        case Configuration.ORIENTATION_PORTRAIT:
          return Device.DeviceOrientation.PORTRAIT;
        default:
          log(SentryLevel.INFO, "No device orientation available (ORIENTATION_UNDEFINED)");
          return null;
      }
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting device orientation.", e);
      return null;
    }
  }

  /**
   * Check whether the application is running in an emulator.
   * https://github.com/flutter/plugins/blob/master/packages/device_info/android/src/main/java/io/flutter/plugins/deviceinfo/DeviceInfoPlugin.java#L105
   *
   * @return true if the application is running in an emulator, false otherwise
   */
  private Boolean isEmulator() {
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
      log(SentryLevel.ERROR, "Error checking whether application is running in an emulator.", e);
      return null;
    }
  }

  /**
   * Get the total amount of internal storage, in bytes.
   *
   * @return the total amount of internal storage, in bytes
   */
  private Long getTotalInternalStorage(StatFs stat) {
    try {
      long blockSize = getBlockSizeLong(stat);
      long totalBlocks = getBlockCountLong(stat);
      return totalBlocks * blockSize;
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting total internal storage amount.", e);
      return null;
    }
  }

  @SuppressWarnings("ObsoleteSdkInt")
  private long getBlockSizeLong(StatFs stat) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      return stat.getBlockSizeLong();
    }
    return getBlockSizeDep(stat);
  }

  @SuppressWarnings("deprecation")
  private int getBlockSizeDep(StatFs stat) {
    return stat.getBlockSize();
  }

  @SuppressWarnings("ObsoleteSdkInt")
  private long getBlockCountLong(StatFs stat) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      return stat.getBlockCountLong();
    }
    return getBlockCountDep(stat);
  }

  @SuppressWarnings("deprecation")
  private int getBlockCountDep(StatFs stat) {
    return stat.getBlockCount();
  }

  @SuppressWarnings("ObsoleteSdkInt")
  private long getAvailableBlocksLong(StatFs stat) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      return stat.getAvailableBlocksLong();
    }
    return getAvailableBlocksDep(stat);
  }

  @SuppressWarnings("deprecation")
  private int getAvailableBlocksDep(StatFs stat) {
    return stat.getAvailableBlocks();
  }

  /**
   * Get the unused amount of internal storage, in bytes.
   *
   * @return the unused amount of internal storage, in bytes
   */
  private Long getUnusedInternalStorage(StatFs stat) {
    try {
      long blockSize = getBlockSizeLong(stat);
      long availableBlocks = getAvailableBlocksLong(stat);
      return availableBlocks * blockSize;
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting unused internal storage amount.", e);
      return null;
    }
  }

  private StatFs getExternalStorageStat(File internalStorage) {
    if (!isExternalStorageMounted()) {
      File path = getExternalStorageDep(internalStorage);
      if (path != null) { // && path.canRead()) { canRead() will read return false
        return new StatFs(path.getPath());
      }
      log(SentryLevel.INFO, "Not possible to read external files directory");
      return null;
    }
    log(SentryLevel.INFO, "External storage is not mounted or emulated.");
    return null;
  }

  @SuppressWarnings("ObsoleteSdkInt")
  public File[] getExternalFilesDirs(String type) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return context.getExternalFilesDirs(type);
    } else {
      File single = context.getExternalFilesDir(type);
      if (single != null) {
        return new File[] {single};
      }
    }
    return null;
  }

  private File getExternalStorageDep(File internalStorage) {
    File[] externalFilesDirs = getExternalFilesDirs(null);

    if (externalFilesDirs != null) {
      // return the 1st file which is not the emulated internal storage
      String internalStoragePath =
          internalStorage != null ? internalStorage.getAbsolutePath() : null;
      for (File file : externalFilesDirs) {
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
      log(SentryLevel.INFO, "Not possible to read getExternalFilesDirs");
    }
    return null;
  }

  /**
   * Get the total amount of external storage, in bytes, or null if no external storage is mounted.
   *
   * @return the total amount of external storage, in bytes, or null if no external storage is
   *     mounted
   */
  private Long getTotalExternalStorage(StatFs stat) {
    try {
      long blockSize = getBlockSizeLong(stat);
      long totalBlocks = getBlockCountLong(stat);
      return totalBlocks * blockSize;
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting total external storage amount.", e);
      return null;
    }
  }

  private boolean isExternalStorageMounted() {
    return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
        && !Environment.isExternalStorageEmulated();
  }

  /**
   * Get the unused amount of external storage, in bytes, or null if no external storage is mounted.
   *
   * @return the unused amount of external storage, in bytes, or null if no external storage is
   *     mounted
   */
  private Long getUnusedExternalStorage(StatFs stat) {
    try {
      long blockSize = getBlockSizeLong(stat);
      long availableBlocks = getAvailableBlocksLong(stat);
      return availableBlocks * blockSize;
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting unused external storage amount.", e);
      return null;
    }
  }

  /**
   * Get the DisplayMetrics object for the current application.
   *
   * @return the DisplayMetrics object for the current application
   */
  private DisplayMetrics getDisplayMetrics() {
    try {
      return context.getResources().getDisplayMetrics();
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting DisplayMetrics.", e);
      return null;
    }
  }

  private OperatingSystem getOperatingSystem() {
    OperatingSystem os = new OperatingSystem();
    os.setName("Android");
    os.setVersion(Build.VERSION.RELEASE);
    os.setBuild(Build.DISPLAY);
    os.setKernelVersion(getKernelVersion());
    os.setRooted(isRooted());

    return os;
  }

  private App getApp(PackageInfo packageInfo) {
    App app = new App();
    app.setAppIdentifier(packageInfo.packageName);

    //    app.setBuildType(); possible with BuildConfig.BUILD_VARIANT but Apps
    // side, also for flavor
    app.setAppVersion(packageInfo.versionName);
    app.setAppBuild(getVersionCode(packageInfo));

    return app;
  }

  /**
   * Get the device's current kernel version, as a string. Attempts to read /proc/version, and falls
   * back to the 'os.version' System Property.
   *
   * @return the device's current kernel version, as a string
   */
  @SuppressWarnings("DefaultCharset")
  private String getKernelVersion() {
    // its possible to try to execute 'uname' and parse it or also another unix commands or even
    // looking for well known root installed apps
    String errorMsg = "Exception while attempting to read kernel information";
    String defaultVersion = System.getProperty("os.version");

    BufferedReader br = null;
    try {
      File file = new File("/proc/version");
      if (!file.canRead()) {
        return defaultVersion;
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        br = Files.newBufferedReader(file.toPath(), UTF_8);
      } else {
        br = new BufferedReader(new FileReader(file));
      }
      return br.readLine();
    } catch (Exception e) {
      log(SentryLevel.ERROR, errorMsg, e);
    } finally {
      if (br != null) {
        try {
          br.close();
        } catch (IOException ioe) {
          log(SentryLevel.ERROR, errorMsg, ioe);
        }
      }
    }

    return defaultVersion;
  }

  /**
   * Attempt to discover if this device is currently rooted. From:
   * https://stackoverflow.com/questions/1101380/determine-if-running-on-a-rooted-device
   *
   * @return true if heuristics show the device is probably rooted, otherwise false
   */
  private Boolean isRooted() {
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
        log(
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
  private String getApplicationName() {
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
      log(SentryLevel.ERROR, "Error getting application name.", e);
    }

    return null;
  }

  public User getUser() {
    User user = new User();

    user.setId(getAndroidId());

    return user;
  }

  @SuppressWarnings("HardwareIds")
  private String getAndroidId() {
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
        log(SentryLevel.ERROR, "Could not generate device Id.", e);

        return null;
      }
    }

    return androidId;
  }

  private String[] getProGuardUuids() {
    InputStream is = null;
    try {
      AssetManager assets = context.getAssets();
      String[] files = assets.list("");

      List<String> listFiles = Arrays.asList(files != null ? files : new String[0]);
      if (listFiles.contains("sentry-debug-meta.properties")) {
        is = assets.open("sentry-debug-meta.properties");
        Properties properties = new Properties();
        properties.load(is);
        is.close();

        String uuid = properties.getProperty("io.sentry.ProguardUuids");
        if (uuid != null && !uuid.isEmpty()) {
          return uuid.split("\\|");
        }
        log(SentryLevel.INFO, "io.sentry.ProguardUuids property was not found.");
      } else {
        log(SentryLevel.INFO, "Proguard UUIDs file not found.");
      }
    } catch (FileNotFoundException e) {
      log(SentryLevel.ERROR, "Proguard UUIDs file not found.", e);
    } catch (Exception e) {
      log(SentryLevel.ERROR, "Error getting Proguard UUIDs.", e);
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (IOException e) {
          log(
              SentryLevel.ERROR,
              "InputStream of sentry-debug-meta.properties didn't close correctly.",
              e);
        }
      }
    }
    return null;
  }

  private void log(SentryLevel level, String message, Throwable throwable) {
    ILogger logger = options.getLogger();
    if (options.isDebug()) {
      logger.log(level, message, throwable);
    }
  }

  private void log(SentryLevel level, String message, Object... args) {
    ILogger logger = options.getLogger();
    if (options.isDebug()) {
      logger.log(level, message, args);
    }
  }
}
