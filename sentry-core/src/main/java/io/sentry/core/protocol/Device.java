package io.sentry.core.protocol;

import java.util.Date;
import java.util.TimeZone;

public class Device {
  static final String TYPE = "device";

  private String name;
  private String manufacturer;
  private String brand;
  private String family;
  private String model;
  private String modelId;
  private String architecture;
  private Short batteryLevel;
  private Boolean isCharging;
  private Boolean isOnline;
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
  private String screenResolution;
  private Float screenDensity;
  private Integer screenDpi;
  private Date bootTime;
  private TimeZone timezone;

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

  public String getArchitecture() {
    return architecture;
  }

  public void setArchitecture(String architecture) {
    this.architecture = architecture;
  }

  public Short getBatteryLevel() {
    return batteryLevel;
  }

  public void setBatteryLevel(Short batteryLevel) {
    this.batteryLevel = batteryLevel;
  }

  public Boolean getCharging() {
    return isCharging;
  }

  public void setCharging(Boolean charging) {
    isCharging = charging;
  }

  public Boolean getOnline() {
    return isOnline;
  }

  public void setOnline(Boolean online) {
    isOnline = online;
  }

  public DeviceOrientation getOrientation() {
    return orientation;
  }

  public void setOrientation(DeviceOrientation orientation) {
    this.orientation = orientation;
  }

  public Boolean getSimulator() {
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

  public Boolean getLowMemory() {
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

  public String getScreenResolution() {
    return screenResolution;
  }

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
    return bootTime;
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

  public enum DeviceOrientation {
    PORTRAIT,
    LANDSCAPE
  }
}
