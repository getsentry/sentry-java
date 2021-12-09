package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class Device implements IUnknownPropertiesConsumer {
  public static final String TYPE = "device";

  /** Name of the device. */
  private @Nullable String name;
  /** Manufacturer of the device. */
  private @Nullable String manufacturer;
  /** Brand of the device. */
  private @Nullable String brand;
  /**
   * Family of the device model.
   *
   * <p>This is usually the common part of model names across generations. For instance, `iPhone`
   * would be a reasonable family, so would be `Samsung Galaxy`.
   */
  private @Nullable String family;
  /**
   * Device model.
   *
   * <p>This, for example, can be `Samsung Galaxy S3`.
   */
  private @Nullable String model;
  /**
   * Device model (internal identifier).
   *
   * <p>An internal hardware revision to identify the device exactly.
   */
  private @Nullable String modelId;

  /** Supported CPU architectures of the device. */
  private @Nullable String[] archs;
  /**
   * Current battery level in %.
   *
   * <p>If the device has a battery, this can be a floating point value defining the battery level
   * (in the range 0-100).
   */
  private @Nullable Float batteryLevel;
  /** Whether the device was charging or not. */
  private @Nullable Boolean charging;
  /** Whether the device was online or not. */
  private @Nullable Boolean online;
  /**
   * Current screen orientation.
   *
   * <p>This can be a string `portrait` or `landscape` to define the orientation of a device.
   */
  private @Nullable DeviceOrientation orientation;
  /** Simulator/prod indicator. */
  private @Nullable Boolean simulator;
  /** Total memory available in bytes. */
  private @Nullable Long memorySize;
  /** How much memory is still available in bytes. */
  private @Nullable Long freeMemory;
  /** How much memory is usable for the app in bytes. */
  private @Nullable Long usableMemory;
  /** Whether the device was low on memory. */
  private @Nullable Boolean lowMemory;
  /** Total storage size of the device in bytes. */
  private @Nullable Long storageSize;
  /** How much storage is free in bytes. */
  private @Nullable Long freeStorage;
  /** Total size of the attached external storage in bytes (eg: android SDK card). */
  private @Nullable Long externalStorageSize;
  /** Free size of the attached external storage in bytes (eg: android SDK card). */
  private @Nullable Long externalFreeStorage;

  /** Device width screen resolution. */
  private @Nullable Integer screenWidthPixels;

  /** Device Height screen resolution. */
  private @Nullable Integer screenHeightPixels;
  /** Device screen density. */
  private @Nullable Float screenDensity;
  /** Screen density as dots-per-inch. */
  private @Nullable Integer screenDpi;
  /** Indicator when the device was booted. */
  private @Nullable Date bootTime;
  /** Timezone of the device. */
  private @Nullable TimeZone timezone;

  private @Nullable String id;

  /**
   * This method returns the language code for this locale, which will either be the empty string or
   * a lowercase ISO 639 code.
   *
   * @deprecated use {@link Device#getLocale()}
   */
  @Deprecated private @Nullable String language;

  /** The locale of the device. For example, en-US. */
  private @Nullable String locale;

  private @Nullable String connectionType;

  /** battery's temperature in celsius */
  private @Nullable Float batteryTemperature;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public Device() {}

  Device(final @NotNull Device device) {
    this.name = device.name;
    this.manufacturer = device.manufacturer;
    this.brand = device.brand;
    this.family = device.family;
    this.model = device.model;
    this.modelId = device.modelId;
    this.charging = device.charging;
    this.online = device.online;
    this.orientation = device.orientation;
    this.simulator = device.simulator;
    this.memorySize = device.memorySize;
    this.freeMemory = device.freeMemory;
    this.usableMemory = device.usableMemory;
    this.lowMemory = device.lowMemory;
    this.storageSize = device.storageSize;
    this.freeStorage = device.freeStorage;
    this.externalStorageSize = device.externalStorageSize;
    this.externalFreeStorage = device.externalFreeStorage;
    this.screenWidthPixels = device.screenWidthPixels;
    this.screenHeightPixels = device.screenHeightPixels;
    this.screenDensity = device.screenDensity;
    this.screenDpi = device.screenDpi;
    this.bootTime = device.bootTime;
    this.id = device.id;
    this.language = device.language;
    this.connectionType = device.connectionType;
    this.batteryTemperature = device.batteryTemperature;
    this.batteryLevel = device.batteryLevel;
    final String[] archsRef = device.archs;
    this.archs = archsRef != null ? archsRef.clone() : null;
    this.locale = device.locale;

    final TimeZone timezoneRef = device.timezone;
    this.timezone = timezoneRef != null ? (TimeZone) timezoneRef.clone() : null;

    this.unknown = CollectionUtils.newConcurrentHashMap(device.unknown);
  }

  public @Nullable String getName() {
    return name;
  }

  public void setName(final @Nullable String name) {
    this.name = name;
  }

  public @Nullable String getManufacturer() {
    return manufacturer;
  }

  public void setManufacturer(final @Nullable String manufacturer) {
    this.manufacturer = manufacturer;
  }

  public @Nullable String getBrand() {
    return brand;
  }

  public void setBrand(final @Nullable String brand) {
    this.brand = brand;
  }

  public @Nullable String getFamily() {
    return family;
  }

  public void setFamily(final @Nullable String family) {
    this.family = family;
  }

  public @Nullable String getModel() {
    return model;
  }

  public void setModel(final @Nullable String model) {
    this.model = model;
  }

  public @Nullable String getModelId() {
    return modelId;
  }

  public void setModelId(final @Nullable String modelId) {
    this.modelId = modelId;
  }

  public @Nullable Float getBatteryLevel() {
    return batteryLevel;
  }

  public void setBatteryLevel(final @Nullable Float batteryLevel) {
    this.batteryLevel = batteryLevel;
  }

  public @Nullable Boolean isCharging() {
    return charging;
  }

  public void setCharging(final @Nullable Boolean charging) {
    this.charging = charging;
  }

  public @Nullable Boolean isOnline() {
    return online;
  }

  public void setOnline(final @Nullable Boolean online) {
    this.online = online;
  }

  public @Nullable DeviceOrientation getOrientation() {
    return orientation;
  }

  public void setOrientation(final @Nullable DeviceOrientation orientation) {
    this.orientation = orientation;
  }

  public @Nullable Boolean isSimulator() {
    return simulator;
  }

  public void setSimulator(final @Nullable Boolean simulator) {
    this.simulator = simulator;
  }

  public @Nullable Long getMemorySize() {
    return memorySize;
  }

  public void setMemorySize(final @Nullable Long memorySize) {
    this.memorySize = memorySize;
  }

  public @Nullable Long getFreeMemory() {
    return freeMemory;
  }

  public void setFreeMemory(final @Nullable Long freeMemory) {
    this.freeMemory = freeMemory;
  }

  public @Nullable Long getUsableMemory() {
    return usableMemory;
  }

  public void setUsableMemory(final @Nullable Long usableMemory) {
    this.usableMemory = usableMemory;
  }

  public @Nullable Boolean isLowMemory() {
    return lowMemory;
  }

  public void setLowMemory(final @Nullable Boolean lowMemory) {
    this.lowMemory = lowMemory;
  }

  public @Nullable Long getStorageSize() {
    return storageSize;
  }

  public void setStorageSize(final @Nullable Long storageSize) {
    this.storageSize = storageSize;
  }

  public @Nullable Long getFreeStorage() {
    return freeStorage;
  }

  public void setFreeStorage(final @Nullable Long freeStorage) {
    this.freeStorage = freeStorage;
  }

  public @Nullable Long getExternalStorageSize() {
    return externalStorageSize;
  }

  public void setExternalStorageSize(final @Nullable Long externalStorageSize) {
    this.externalStorageSize = externalStorageSize;
  }

  public @Nullable Long getExternalFreeStorage() {
    return externalFreeStorage;
  }

  public void setExternalFreeStorage(final @Nullable Long externalFreeStorage) {
    this.externalFreeStorage = externalFreeStorage;
  }

  public @Nullable Float getScreenDensity() {
    return screenDensity;
  }

  public void setScreenDensity(final @Nullable Float screenDensity) {
    this.screenDensity = screenDensity;
  }

  public @Nullable Integer getScreenDpi() {
    return screenDpi;
  }

  public void setScreenDpi(final @Nullable Integer screenDpi) {
    this.screenDpi = screenDpi;
  }

  @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
  public @Nullable Date getBootTime() {
    final Date bootTimeRef = bootTime;
    return bootTimeRef != null ? (Date) bootTimeRef.clone() : null;
  }

  public void setBootTime(final @Nullable Date bootTime) {
    this.bootTime = bootTime;
  }

  public @Nullable TimeZone getTimezone() {
    return timezone;
  }

  public void setTimezone(final @Nullable TimeZone timezone) {
    this.timezone = timezone;
  }

  public @Nullable String[] getArchs() {
    return archs;
  }

  public void setArchs(final @Nullable String[] archs) {
    this.archs = archs;
  }

  public @Nullable Integer getScreenWidthPixels() {
    return screenWidthPixels;
  }

  public void setScreenWidthPixels(final @Nullable Integer screenWidthPixels) {
    this.screenWidthPixels = screenWidthPixels;
  }

  public @Nullable Integer getScreenHeightPixels() {
    return screenHeightPixels;
  }

  public void setScreenHeightPixels(final @Nullable Integer screenHeightPixels) {
    this.screenHeightPixels = screenHeightPixels;
  }

  public @Nullable String getId() {
    return id;
  }

  public void setId(final @Nullable String id) {
    this.id = id;
  }

  public @Nullable String getLanguage() {
    return language;
  }

  public void setLanguage(final @Nullable String language) {
    this.language = language;
  }

  public @Nullable String getConnectionType() {
    return connectionType;
  }

  public void setConnectionType(final @Nullable String connectionType) {
    this.connectionType = connectionType;
  }

  public @Nullable Float getBatteryTemperature() {
    return batteryTemperature;
  }

  public void setBatteryTemperature(final @Nullable Float batteryTemperature) {
    this.batteryTemperature = batteryTemperature;
  }

  public @Nullable String getLocale() {
    return locale;
  }

  public void setLocale(final @Nullable String locale) {
    this.locale = locale;
  }

  @TestOnly
  @Nullable
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  public enum DeviceOrientation {
    PORTRAIT,
    LANDSCAPE
  }
}
