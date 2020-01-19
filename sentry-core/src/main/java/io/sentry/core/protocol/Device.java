package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;

public final class Device implements IUnknownPropertiesConsumer {
  public static final String TYPE = "device";

  private String name;
  private String manufacturer;
  private String brand;
  private String family;
  private String model;
  private String modelId;

  @Deprecated private String arch;

  private String[] archs;
  private Float batteryLevel;
  private Boolean charging;
  private Boolean online;
  private DeviceOrientation orientation;
  private Boolean simulator;
  private Long memorySize;
  private Long freeMemory;
  private Long usableMemory;
  private Boolean lowMemory;
  private Long storageSize;
  private Long freeStorage;
  private Long externalStorageSize;
  private Long externalFreeStorage;

  @Deprecated private String screenResolution;

  private Integer screenWidthPixels;
  private Integer screenHeightPixels;

  private Float screenDensity;
  private Integer screenDpi;
  private Date bootTime;
  private TimeZone timezone;
  private String id;
  private String language;
  private String connectionType;

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
   * @deprecated use {@link #getArchs} instead.
   * @return device architecture
   */
  @Deprecated
  public String getArch() {
    return arch;
  }

  /**
   * @deprecated use {@link #setArchs} instead.
   * @param arch device architecture
   */
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
   * @deprecated use {@link #getScreenWidthPixels , #getScreenHeightPixels} instead.
   * @return screen resolution largest + smallest
   */
  @Deprecated
  public String getScreenResolution() {
    return screenResolution;
  }

  /**
   * @deprecated use {@link #setScreenWidthPixels} , #getScreenHeightPixels} instead.
   * @param screenResolution screen resolution largest + smallest
   */
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

  public enum DeviceOrientation {
    PORTRAIT,
    LANDSCAPE
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
