package io.sentry.android.core;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED;
import static android.os.BatteryManager.EXTRA_TEMPERATURE;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import io.sentry.DateUtils;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.AndroidMainThreadChecker;
import io.sentry.android.core.internal.util.ConnectivityChecker;
import io.sentry.android.core.internal.util.DeviceOrientations;
import io.sentry.android.core.internal.util.RootChecker;
import io.sentry.protocol.App;
import io.sentry.protocol.Device;
import io.sentry.protocol.OperatingSystem;
import io.sentry.protocol.SentryThread;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class DefaultAndroidEventProcessor implements EventProcessor {

  @TestOnly static final String ROOTED = "rooted";
  @TestOnly static final String KERNEL_VERSION = "kernelVersion";
  @TestOnly static final String EMULATOR = "emulator";
  @TestOnly static final String SIDE_LOADED = "sideLoaded";

  @TestOnly final Context context;

  @TestOnly final Future<Map<String, Object>> contextData;

  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull RootChecker rootChecker;
  private final @NotNull SentryAndroidOptions options;

  public DefaultAndroidEventProcessor(
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull SentryAndroidOptions options) {
    this(
        context,
        buildInfoProvider,
        new RootChecker(context, buildInfoProvider, options.getLogger()),
        options);
  }

  DefaultAndroidEventProcessor(
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull RootChecker rootChecker,
      final @NotNull SentryAndroidOptions options) {
    this.context = Objects.requireNonNull(context, "The application context is required.");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "The BuildInfoProvider is required.");
    this.rootChecker = Objects.requireNonNull(rootChecker, "The RootChecker is required.");
    this.options = Objects.requireNonNull(options, "The options object is required.");

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    // dont ref. to method reference, theres a bug on it
    //noinspection Convert2MethodRef
    contextData = executorService.submit(() -> loadContextData());

    executorService.shutdown();
  }

  private @NotNull Map<String, Object> loadContextData() {
    Map<String, Object> map = new HashMap<>();

    map.put(ROOTED, rootChecker.isDeviceRooted());

    String kernelVersion = ContextUtils.getKernelVersion(options.getLogger());
    if (kernelVersion != null) {
      map.put(KERNEL_VERSION, kernelVersion);
    }

    // its not IO, but it has been cached in the old version as well
    map.put(EMULATOR, buildInfoProvider.isEmulator());

    final Map<String, String> sideLoadedInfo =
      ContextUtils.getSideLoadedInfo(context, options.getLogger(), buildInfoProvider);
    if (sideLoadedInfo != null) {
      map.put(SIDE_LOADED, sideLoadedInfo);
    }

    return map;
  }

  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    final boolean applyScopeData = shouldApplyScopeData(event, hint);
    if (applyScopeData) {
      // we only set memory data if it's not a hard crash, when it's a hard crash the event is
      // enriched on restart, so non static data might be wrong, eg lowMemory or availMem will
      // be different if the App. crashes because of OOM.
      processNonCachedEvent(event);
      setThreads(event);
    }

    setCommons(event, true, applyScopeData);

    return event;
  }

  private void setCommons(
      final @NotNull SentryBaseEvent event,
      final boolean errorEvent,
      final boolean applyScopeData) {
    mergeUser(event);
    setDevice(event, errorEvent, applyScopeData);
    mergeOS(event);
    setSideLoadedInfo(event);
  }

  private boolean shouldApplyScopeData(
      final @NotNull SentryBaseEvent event, final @NotNull Hint hint) {
    if (HintUtils.shouldApplyScopeData(hint)) {
      return true;
    } else {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Event was cached so not applying data relevant to the current app execution/version: %s",
              event.getEventId());
      return false;
    }
  }

  private void mergeUser(final @NotNull SentryBaseEvent event) {
    // userId should be set even if event is Cached as the userId is static and won't change anyway.
    final User user = event.getUser();
    if (user == null) {
      event.setUser(getDefaultUser());
    } else if (user.getId() == null) {
      user.setId(getDeviceId());
    }
  }

  private void setDevice(
      final @NotNull SentryBaseEvent event,
      final boolean errorEvent,
      final boolean applyScopeData) {
    if (event.getContexts().getDevice() == null) {
      event.getContexts().setDevice(getDevice(errorEvent, applyScopeData));
    }
  }

  private void mergeOS(final @NotNull SentryBaseEvent event) {
    final OperatingSystem currentOS = event.getContexts().getOperatingSystem();
    final OperatingSystem androidOS = getOperatingSystem();

    // make Android OS the main OS using the 'os' key
    event.getContexts().setOperatingSystem(androidOS);

    if (currentOS != null) {
      // add additional OS which was already part of the SentryEvent (eg Linux read from NDK)
      String osNameKey = currentOS.getName();
      if (osNameKey != null && !osNameKey.isEmpty()) {
        osNameKey = "os_" + osNameKey.trim().toLowerCase(Locale.ROOT);
      } else {
        osNameKey = "os_1";
      }
      event.getContexts().put(osNameKey, currentOS);
    }
  }

  // Data to be applied to events that was created in the running process
  private void processNonCachedEvent(final @NotNull SentryBaseEvent event) {
    App app = event.getContexts().getApp();
    if (app == null) {
      app = new App();
    }
    setAppExtras(app);

    setPackageInfo(event, app);

    event.getContexts().setApp(app);
  }

  private void setThreads(final @NotNull SentryEvent event) {
    if (event.getThreads() != null) {
      for (SentryThread thread : event.getThreads()) {
        if (thread.isCurrent() == null) {
          thread.setCurrent(AndroidMainThreadChecker.getInstance().isMainThread(thread));
        }
      }
    }
  }

  private void setPackageInfo(final @NotNull SentryBaseEvent event, final @NotNull App app) {
    final PackageInfo packageInfo =
        ContextUtils.getPackageInfo(
            context, PackageManager.GET_PERMISSIONS, options.getLogger(), buildInfoProvider);
    if (packageInfo != null) {
      String versionCode = ContextUtils.getVersionCode(packageInfo, buildInfoProvider);

      setDist(event, versionCode);
      setAppPackageInfo(app, packageInfo);
    }
  }

  private void setDist(final @NotNull SentryBaseEvent event, final @NotNull String versionCode) {
    if (event.getDist() == null) {
      event.setDist(versionCode);
    }
  }

  private void setAppExtras(final @NotNull App app) {
    app.setAppName(ContextUtils.getApplicationName(context, options.getLogger()));
    app.setAppStartTime(DateUtils.toUtilDate(AppStartState.getInstance().getAppStartTime()));
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
  private @NotNull Device getDevice(final boolean errorEvent, final boolean applyScopeData) {
    // TODO: missing usable memory

    Device device = new Device();
    if (options.isSendDefaultPii()) {
      device.setName(ContextUtils.getDeviceName(context, buildInfoProvider));
    }
    device.setManufacturer(Build.MANUFACTURER);
    device.setBrand(Build.BRAND);
    device.setFamily(ContextUtils.getFamily(options.getLogger()));
    device.setModel(Build.MODEL);
    device.setModelId(Build.ID);
    device.setArchs(ContextUtils.getArchitectures(buildInfoProvider));

    // setting such values require IO hence we don't run for transactions
    if (errorEvent) {
      if (options.isCollectAdditionalContext()) {
        setDeviceIO(device, applyScopeData);
      }
    }

    device.setOrientation(getOrientation());

    try {
      Object emulator = contextData.get().get(EMULATOR);
      if (emulator != null) {
        device.setSimulator((Boolean) emulator);
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting emulator.", e);
    }

    DisplayMetrics displayMetrics = ContextUtils.getDisplayMetrics(context, options.getLogger());
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

    final Locale locale = Locale.getDefault();
    if (device.getLanguage() == null) {
      device.setLanguage(locale.getLanguage());
    }
    if (device.getLocale() == null) {
      device.setLocale(locale.toString()); // eg en_US
    }

    return device;
  }

  private void setDeviceIO(final @NotNull Device device, final boolean applyScopeData) {
    final Intent batteryIntent = getBatteryIntent();
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

    final ActivityManager.MemoryInfo memInfo = ContextUtils.getMemInfo(context, options.getLogger());
    if (memInfo != null) {
      // in bytes
      device.setMemorySize(getMemorySize(memInfo));
      if (applyScopeData) {
        device.setFreeMemory(memInfo.availMem);
        device.setLowMemory(memInfo.lowMemory);
      }
      // there are runtime.totalMemory() and runtime.freeMemory(), but I kept the same for
      // compatibility
    }

    // this way of getting the size of storage might be problematic for storages bigger than 2GB
    // check the use of
    // https://developer.android.com/reference/java/io/File.html#getFreeSpace%28%29
    final File internalStorageFile = context.getExternalFilesDir(null);
    if (internalStorageFile != null) {
      StatFs internalStorageStat = new StatFs(internalStorageFile.getPath());
      device.setStorageSize(getTotalInternalStorage(internalStorageStat));
      device.setFreeStorage(getUnusedInternalStorage(internalStorageStat));
    }

    final StatFs externalStorageStat = getExternalStorageStat(internalStorageFile);
    if (externalStorageStat != null) {
      device.setExternalStorageSize(getTotalExternalStorage(externalStorageStat));
      device.setExternalFreeStorage(getUnusedExternalStorage(externalStorageStat));
    }

    if (device.getConnectionType() == null) {
      // wifi, ethernet or cellular, null if none
      device.setConnectionType(
          ConnectivityChecker.getConnectionType(context, options.getLogger(), buildInfoProvider));
    }
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

  @SuppressWarnings("JdkObsolete")
  private @Nullable Date getBootTime() {
    try {
      // if user changes the clock, will give a wrong answer, consider ACTION_TIME_CHANGED.
      // currentTimeMillis returns UTC already
      return DateUtils.getDateTime(System.currentTimeMillis() - SystemClock.elapsedRealtime());
    } catch (IllegalArgumentException e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Error getting the device's boot time.");
    }
    return null;
  }

  private @Nullable Intent getBatteryIntent() {
    return context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
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
  private @Nullable Boolean isCharging(final @NotNull Intent batteryIntent) {
    try {
      int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
      return plugged == BatteryManager.BATTERY_PLUGGED_AC
          || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    } catch (Throwable e) {
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
  private @Nullable Long getTotalInternalStorage(final @NotNull StatFs stat) {
    try {
      long blockSize = getBlockSizeLong(stat);
      long totalBlocks = getBlockCountLong(stat);
      return totalBlocks * blockSize;
    } catch (Throwable e) {
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
    } catch (Throwable e) {
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
  private @Nullable Long getUnusedExternalStorage(final @NotNull StatFs stat) {
    try {
      long blockSize = getBlockSizeLong(stat);
      long availableBlocks = getAvailableBlocksLong(stat);
      return availableBlocks * blockSize;
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Error getting unused external storage amount.", e);
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
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting OperatingSystem.", e);
    }

    return os;
  }

  @SuppressLint("NewApi") // we perform an if-check for that, but lint fails to recognize
  private void setAppPackageInfo(final @NotNull App app, final @NotNull PackageInfo packageInfo) {
    app.setAppIdentifier(packageInfo.packageName);
    app.setAppVersion(packageInfo.versionName);
    app.setAppBuild(ContextUtils.getVersionCode(packageInfo, buildInfoProvider));

    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.JELLY_BEAN) {
      final Map<String, String> permissions = new HashMap<>();
      final String[] requestedPermissions = packageInfo.requestedPermissions;
      final int[] requestedPermissionsFlags = packageInfo.requestedPermissionsFlags;

      if (requestedPermissions != null
          && requestedPermissions.length > 0
          && requestedPermissionsFlags != null
          && requestedPermissionsFlags.length > 0) {
        for (int i = 0; i < requestedPermissions.length; i++) {
          String permission = requestedPermissions[i];
          permission = permission.substring(permission.lastIndexOf('.') + 1);

          final boolean granted =
              (requestedPermissionsFlags[i] & REQUESTED_PERMISSION_GRANTED)
                  == REQUESTED_PERMISSION_GRANTED;
          permissions.put(permission, granted ? "granted" : "not_granted");
        }
      }
      app.setPermissions(permissions);
    }
  }

  /**
   * Sets the default user which contains only the userId.
   *
   * @return the User object
   */
  public @NotNull User getDefaultUser() {
    User user = new User();
    user.setId(getDeviceId());

    return user;
  }

  private @Nullable String getDeviceId() {
    try {
      return Installation.id(context);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting installationId.", e);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private void setSideLoadedInfo(final @NotNull SentryBaseEvent event) {
    try {
      final Object sideLoadedInfo = contextData.get().get(SIDE_LOADED);

      if (sideLoadedInfo instanceof Map) {
        for (final Map.Entry<String, String> entry :
            ((Map<String, String>) sideLoadedInfo).entrySet()) {
          event.setTag(entry.getKey(), entry.getValue());
        }
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting side loaded info.", e);
    }
  }

  @Override
  public @NotNull SentryTransaction process(
      final @NotNull SentryTransaction transaction, final @NotNull Hint hint) {
    final boolean applyScopeData = shouldApplyScopeData(transaction, hint);

    if (applyScopeData) {
      processNonCachedEvent(transaction);
    }

    setCommons(transaction, false, applyScopeData);

    return transaction;
  }
}
