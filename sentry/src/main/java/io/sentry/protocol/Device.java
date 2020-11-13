package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class Device implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "device";

  /** Name of the device. */
  private String name;
  /** Manufacturer of the device. */
  private String manufacturer;
  /** Brand of the device. */
  private String brand;
  /**
   * Family of the device model.
   *
   * <p>This is usually the common part of model names across generations. For instance, `iPhone`
   * would be a reasonable family, so would be `Samsung Galaxy`.
   */
  private String family;
  /**
   * Device model.
   *
   * <p>This, for example, can be `Samsung Galaxy S3`.
   */
  private String model;
  /**
   * Device model (internal identifier).
   *
   * <p>An internal hardware revision to identify the device exactly.
   */
  private String modelId;

  /** Native cpu architecture of the device. */
  @ApiStatus.ScheduledForRemoval @Deprecated private String arch;

  /** Native cpu architecture of the device. */
  private String[] archs;
  /**
   * Current battery level in %.
   *
   * <p>If the device has a battery, this can be a floating point value defining the battery level
   * (in the range 0-100).
   */
  private Float batteryLevel;
  /** Whether the device was charging or not. */
  private Boolean charging;
  /** Whether the device was online or not. */
  private Boolean online;
  /**
   * Current screen orientation.
   *
   * <p>This can be a string `portrait` or `landscape` to define the orientation of a device.
   */
  private DeviceOrientation orientation;
  /** Simulator/prod indicator. */
  private Boolean simulator;
  /** Total memory available in bytes. */
  private Long memorySize;
  /** How much memory is still available in bytes. */
  private Long freeMemory;
  /** How much memory is usable for the app in bytes. */
  private Long usableMemory;
  /** Whether the device was low on memory. */
  private Boolean lowMemory;
  /** Total storage size of the device in bytes. */
  private Long storageSize;
  /** How much storage is free in bytes. */
  private Long freeStorage;
  /** Total size of the attached external storage in bytes (eg: android SDK card). */
  private Long externalStorageSize;
  /** Free size of the attached external storage in bytes (eg: android SDK card). */
  private Long externalFreeStorage;
  /**
   * Device screen resolution.
   *
   * <p>(e.g.: 800x600, 3040x1444)
   */
  @ApiStatus.ScheduledForRemoval @Deprecated private String screenResolution;

  private Integer screenWidthPixels;
  private Integer screenHeightPixels;
  /** Device screen density. */
  private Float screenDensity;
  /** Screen density as dots-per-inch. */
  private Integer screenDpi;
  /** Indicator when the device was booted. */
  private Date bootTime;
  /** Timezone of the device. */
  private TimeZone timezone;

  private String id;
  private String language;
  private String connectionType;

  /** battery's temperature in celsius */
  private Float batteryTemperature;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getManufacturer() {
    return manufacturer;
  }

  public void setManufacturer(String manufacturer) {
    this.manufacturer = manufacturer;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String brand) {
    this.brand = brand;
  }

  public String getFamily() {
    return family;
  }

  public void setFamily(String family) {
    this.family = family;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(String modelId) {
    this.modelId = modelId;
  }

  /**
   * Returns the arch String
   *
   * @return device architecture
   * @deprecated use {@link #getArchs} instead.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public String getArch() {
    return arch;
  }

  /**
   * @param arch device architecture
   * @deprecated use {@link #setArchs} instead.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public void setArch(String arch) {
    this.arch = arch;
  }

  public Float getBatteryLevel() {
    return batteryLevel;
  }

  public void setBatteryLevel(Float batteryLevel) {
    this.batteryLevel = batteryLevel;
  }

  public Boolean isCharging() {
    return charging;
  }

  public void setCharging(Boolean charging) {
    this.charging = charging;
  }

  public Boolean isOnline() {
    return online;
  }

  public void setOnline(Boolean online) {
    this.online = online;
  }

  public DeviceOrientation getOrientation() {
    return orientation;
  }

  public void setOrientation(DeviceOrientation orientation) {
    this.orientation = orientation;
  }

  public Boolean isSimulator() {
    return simulator;
  }

  public void setSimulator(Boolean simulator) {
    this.simulator = simulator;
  }

  public Long getMemorySize() {
    return memorySize;
  }

  public void setMemorySize(Long memorySize) {
    this.memorySize = memorySize;
  }

  public Long getFreeMemory() {
    return freeMemory;
  }

  public void setFreeMemory(Long freeMemory) {
    this.freeMemory = freeMemory;
  }

  public Long getUsableMemory() {
    return usableMemory;
  }

  public void setUsableMemory(Long usableMemory) {
    this.usableMemory = usableMemory;
  }

  public Boolean isLowMemory() {
    return lowMemory;
  }

  public void setLowMemory(Boolean lowMemory) {
    this.lowMemory = lowMemory;
  }

  public Long getStorageSize() {
    return storageSize;
  }

  public void setStorageSize(Long storageSize) {
    this.storageSize = storageSize;
  }

  public Long getFreeStorage() {
    return freeStorage;
  }

  public void setFreeStorage(Long freeStorage) {
    this.freeStorage = freeStorage;
  }

  public Long getExternalStorageSize() {
    return externalStorageSize;
  }

  public void setExternalStorageSize(Long externalStorageSize) {
    this.externalStorageSize = externalStorageSize;
  }

  public Long getExternalFreeStorage() {
    return externalFreeStorage;
  }

  public void setExternalFreeStorage(Long externalFreeStorage) {
    this.externalFreeStorage = externalFreeStorage;
  }

  /**
   * Returns the screenResolution String
   *
   * @return screen resolution largest + smallest
   * @deprecated use {@link #getScreenWidthPixels , #getScreenHeightPixels} instead.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public String getScreenResolution() {
    return screenResolution;
  }

  /**
   * @param screenResolution screen resolution largest + smallest
   * @deprecated use {@link #setScreenWidthPixels} , #getScreenHeightPixels} instead.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public void setScreenResolution(String screenResolution) {
    this.screenResolution = screenResolution;
  }

  public Float getScreenDensity() {
    return screenDensity;
  }

  public void setScreenDensity(Float screenDensity) {
    this.screenDensity = screenDensity;
  }

  public Integer getScreenDpi() {
    return screenDpi;
  }

  public void setScreenDpi(Integer screenDpi) {
    this.screenDpi = screenDpi;
  }

  @SuppressWarnings("JdkObsolete")
  public Date getBootTime() {
    final Date bootTimeRef = bootTime;
    return bootTimeRef != null ? (Date) bootTimeRef.clone() : null;
  }

  public void setBootTime(Date bootTime) {
    this.bootTime = bootTime;
  }

  public TimeZone getTimezone() {
    return timezone;
  }

  public void setTimezone(TimeZone timezone) {
    this.timezone = timezone;
  }

  public String[] getArchs() {
    return archs;
  }

  public void setArchs(String[] archs) {
    this.archs = archs;
  }

  public Integer getScreenWidthPixels() {
    return screenWidthPixels;
  }

  public void setScreenWidthPixels(Integer screenWidthPixels) {
    this.screenWidthPixels = screenWidthPixels;
  }

  public Integer getScreenHeightPixels() {
    return screenHeightPixels;
  }

  public void setScreenHeightPixels(Integer screenHeightPixels) {
    this.screenHeightPixels = screenHeightPixels;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getConnectionType() {
    return connectionType;
  }

  public void setConnectionType(String connectionType) {
    this.connectionType = connectionType;
  }

  public Float getBatteryTemperature() {
    return batteryTemperature;
  }

  public void setBatteryTemperature(Float batteryTemperature) {
    this.batteryTemperature = batteryTemperature;
  }

  @TestOnly
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  /**
   * Clones a Device aka deep copy
   *
   * @return the cloned Device
   * @throws CloneNotSupportedException if object is not cloneable
   */
  @Override
  public @NotNull Device clone() throws CloneNotSupportedException {
    final Device clone = (Device) super.clone();

    final String[] archsRef = this.archs;
    clone.archs = archsRef != null ? this.archs.clone() : null;

    final TimeZone timezoneRef = this.timezone;
    clone.timezone = timezoneRef != null ? (TimeZone) this.timezone.clone() : null;

    clone.unknown = CollectionUtils.shallowCopy(unknown);

    return clone;
  }

  public enum DeviceOrientation {
    PORTRAIT,
    LANDSCAPE
  }
}
