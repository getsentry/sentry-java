package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Device implements JsonUnknown, JsonSerializable {
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

  /** The locale of the device. For example, en-US. */
  private @Nullable String locale;

  private @Nullable String connectionType;

  /** battery's temperature in celsius */
  private @Nullable Float batteryTemperature;

  /** Optional. Number of "logical processors". For example, 8. */
  private @Nullable Integer processorCount;

  /**
   * Optional. Processor frequency in MHz. Note that the actual CPU frequency might vary depending
   * on current load and power conditions, especially on low-powered devices like phones and
   * laptops.
   */
  private @Nullable Double processorFrequency;

  /** Optional. CPU description. For example, Intel(R) Core(TM)2 Quad CPU Q6600 @ 2.40GHz. */
  private @Nullable String cpuDescription;

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
    this.connectionType = device.connectionType;
    this.batteryTemperature = device.batteryTemperature;
    this.batteryLevel = device.batteryLevel;
    final String[] archsRef = device.archs;
    this.archs = archsRef != null ? archsRef.clone() : null;
    this.locale = device.locale;

    final TimeZone timezoneRef = device.timezone;
    this.timezone = timezoneRef != null ? (TimeZone) timezoneRef.clone() : null;

    this.processorCount = device.processorCount;
    this.processorFrequency = device.processorFrequency;
    this.cpuDescription = device.cpuDescription;

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

  public @Nullable Integer getProcessorCount() {
    return processorCount;
  }

  public void setProcessorCount(@Nullable final Integer processorCount) {
    this.processorCount = processorCount;
  }

  public @Nullable Double getProcessorFrequency() {
    return processorFrequency;
  }

  public void setProcessorFrequency(@Nullable final Double processorFrequency) {
    this.processorFrequency = processorFrequency;
  }

  public @Nullable String getCpuDescription() {
    return cpuDescription;
  }

  public void setCpuDescription(@Nullable final String cpuDescription) {
    this.cpuDescription = cpuDescription;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Device device = (Device) o;
    return Objects.equals(name, device.name)
        && Objects.equals(manufacturer, device.manufacturer)
        && Objects.equals(brand, device.brand)
        && Objects.equals(family, device.family)
        && Objects.equals(model, device.model)
        && Objects.equals(modelId, device.modelId)
        && Arrays.equals(archs, device.archs)
        && Objects.equals(batteryLevel, device.batteryLevel)
        && Objects.equals(charging, device.charging)
        && Objects.equals(online, device.online)
        && orientation == device.orientation
        && Objects.equals(simulator, device.simulator)
        && Objects.equals(memorySize, device.memorySize)
        && Objects.equals(freeMemory, device.freeMemory)
        && Objects.equals(usableMemory, device.usableMemory)
        && Objects.equals(lowMemory, device.lowMemory)
        && Objects.equals(storageSize, device.storageSize)
        && Objects.equals(freeStorage, device.freeStorage)
        && Objects.equals(externalStorageSize, device.externalStorageSize)
        && Objects.equals(externalFreeStorage, device.externalFreeStorage)
        && Objects.equals(screenWidthPixels, device.screenWidthPixels)
        && Objects.equals(screenHeightPixels, device.screenHeightPixels)
        && Objects.equals(screenDensity, device.screenDensity)
        && Objects.equals(screenDpi, device.screenDpi)
        && Objects.equals(bootTime, device.bootTime)
        && Objects.equals(id, device.id)
        && Objects.equals(locale, device.locale)
        && Objects.equals(connectionType, device.connectionType)
        && Objects.equals(batteryTemperature, device.batteryTemperature)
        && Objects.equals(processorCount, device.processorCount)
        && Objects.equals(processorFrequency, device.processorFrequency)
        && Objects.equals(cpuDescription, device.cpuDescription);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            name,
            manufacturer,
            brand,
            family,
            model,
            modelId,
            batteryLevel,
            charging,
            online,
            orientation,
            simulator,
            memorySize,
            freeMemory,
            usableMemory,
            lowMemory,
            storageSize,
            freeStorage,
            externalStorageSize,
            externalFreeStorage,
            screenWidthPixels,
            screenHeightPixels,
            screenDensity,
            screenDpi,
            bootTime,
            timezone,
            id,
            locale,
            connectionType,
            batteryTemperature,
            processorCount,
            processorFrequency,
            cpuDescription);
    result = 31 * result + Arrays.hashCode(archs);
    return result;
  }

  public enum DeviceOrientation implements JsonSerializable {
    PORTRAIT,
    LANDSCAPE;

    // JsonElementSerializer

    @Override
    public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
        throws IOException {
      writer.value(toString().toLowerCase(Locale.ROOT));
    }

    // JsonElementDeserializer

    public static final class Deserializer implements JsonDeserializer<DeviceOrientation> {
      @Override
      public @NotNull DeviceOrientation deserialize(
          @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
        return DeviceOrientation.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
      }
    }
  }

  // region JsonSerializable

  public static final class JsonKeys {
    public static final String NAME = "name";
    public static final String MANUFACTURER = "manufacturer";
    public static final String BRAND = "brand";
    public static final String FAMILY = "family";
    public static final String MODEL = "model";
    public static final String MODEL_ID = "model_id";
    public static final String ARCHS = "archs";
    public static final String BATTERY_LEVEL = "battery_level";
    public static final String CHARGING = "charging";
    public static final String ONLINE = "online";
    public static final String ORIENTATION = "orientation";
    public static final String SIMULATOR = "simulator";
    public static final String MEMORY_SIZE = "memory_size";
    public static final String FREE_MEMORY = "free_memory";
    public static final String USABLE_MEMORY = "usable_memory";
    public static final String LOW_MEMORY = "low_memory";
    public static final String STORAGE_SIZE = "storage_size";
    public static final String FREE_STORAGE = "free_storage";
    public static final String EXTERNAL_STORAGE_SIZE = "external_storage_size";
    public static final String EXTERNAL_FREE_STORAGE = "external_free_storage";
    public static final String SCREEN_WIDTH_PIXELS = "screen_width_pixels";
    public static final String SCREEN_HEIGHT_PIXELS = "screen_height_pixels";
    public static final String SCREEN_DENSITY = "screen_density";
    public static final String SCREEN_DPI = "screen_dpi";
    public static final String BOOT_TIME = "boot_time";
    public static final String TIMEZONE = "timezone";
    public static final String ID = "id";
    public static final String CONNECTION_TYPE = "connection_type";
    public static final String BATTERY_TEMPERATURE = "battery_temperature";
    public static final String LOCALE = "locale";
    public static final String PROCESSOR_COUNT = "processor_count";
    public static final String CPU_DESCRIPTION = "cpu_description";
    public static final String PROCESSOR_FREQUENCY = "processor_frequency";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (name != null) {
      writer.name(JsonKeys.NAME).value(name);
    }
    if (manufacturer != null) {
      writer.name(JsonKeys.MANUFACTURER).value(manufacturer);
    }
    if (brand != null) {
      writer.name(JsonKeys.BRAND).value(brand);
    }
    if (family != null) {
      writer.name(JsonKeys.FAMILY).value(family);
    }
    if (model != null) {
      writer.name(JsonKeys.MODEL).value(model);
    }
    if (modelId != null) {
      writer.name(JsonKeys.MODEL_ID).value(modelId);
    }
    if (archs != null) {
      writer.name(JsonKeys.ARCHS).value(logger, archs);
    }
    if (batteryLevel != null) {
      writer.name(JsonKeys.BATTERY_LEVEL).value(batteryLevel);
    }
    if (charging != null) {
      writer.name(JsonKeys.CHARGING).value(charging);
    }
    if (online != null) {
      writer.name(JsonKeys.ONLINE).value(online);
    }
    if (orientation != null) {
      writer.name(JsonKeys.ORIENTATION).value(logger, orientation);
    }
    if (simulator != null) {
      writer.name(JsonKeys.SIMULATOR).value(simulator);
    }
    if (memorySize != null) {
      writer.name(JsonKeys.MEMORY_SIZE).value(memorySize);
    }
    if (freeMemory != null) {
      writer.name(JsonKeys.FREE_MEMORY).value(freeMemory);
    }
    if (usableMemory != null) {
      writer.name(JsonKeys.USABLE_MEMORY).value(usableMemory);
    }
    if (lowMemory != null) {
      writer.name(JsonKeys.LOW_MEMORY).value(lowMemory);
    }
    if (storageSize != null) {
      writer.name(JsonKeys.STORAGE_SIZE).value(storageSize);
    }
    if (freeStorage != null) {
      writer.name(JsonKeys.FREE_STORAGE).value(freeStorage);
    }
    if (externalStorageSize != null) {
      writer.name(JsonKeys.EXTERNAL_STORAGE_SIZE).value(externalStorageSize);
    }
    if (externalFreeStorage != null) {
      writer.name(JsonKeys.EXTERNAL_FREE_STORAGE).value(externalFreeStorage);
    }
    if (screenWidthPixels != null) {
      writer.name(JsonKeys.SCREEN_WIDTH_PIXELS).value(screenWidthPixels);
    }
    if (screenHeightPixels != null) {
      writer.name(JsonKeys.SCREEN_HEIGHT_PIXELS).value(screenHeightPixels);
    }
    if (screenDensity != null) {
      writer.name(JsonKeys.SCREEN_DENSITY).value(screenDensity);
    }
    if (screenDpi != null) {
      writer.name(JsonKeys.SCREEN_DPI).value(screenDpi);
    }
    if (bootTime != null) {
      writer.name(JsonKeys.BOOT_TIME).value(logger, bootTime);
    }
    if (timezone != null) {
      writer.name(JsonKeys.TIMEZONE).value(logger, timezone);
    }
    if (id != null) {
      writer.name(JsonKeys.ID).value(id);
    }
    if (connectionType != null) {
      writer.name(JsonKeys.CONNECTION_TYPE).value(connectionType);
    }
    if (batteryTemperature != null) {
      writer.name(JsonKeys.BATTERY_TEMPERATURE).value(batteryTemperature);
    }
    if (locale != null) {
      writer.name(JsonKeys.LOCALE).value(locale);
    }
    if (processorCount != null) {
      writer.name(JsonKeys.PROCESSOR_COUNT).value(processorCount);
    }
    if (processorFrequency != null) {
      writer.name(JsonKeys.PROCESSOR_FREQUENCY).value(processorFrequency);
    }
    if (cpuDescription != null) {
      writer.name(JsonKeys.CPU_DESCRIPTION).value(cpuDescription);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  public @Nullable String getLocale() {
    return locale;
  }

  public void setLocale(final @Nullable String locale) {
    this.locale = locale;
  }

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<Device> {

    @Override
    public @NotNull Device deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      Device device = new Device();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.NAME:
            device.name = reader.nextStringOrNull();
            break;
          case JsonKeys.MANUFACTURER:
            device.manufacturer = reader.nextStringOrNull();
            break;
          case JsonKeys.BRAND:
            device.brand = reader.nextStringOrNull();
            break;
          case JsonKeys.FAMILY:
            device.family = reader.nextStringOrNull();
            break;
          case JsonKeys.MODEL:
            device.model = reader.nextStringOrNull();
            break;
          case JsonKeys.MODEL_ID:
            device.modelId = reader.nextStringOrNull();
            break;
          case JsonKeys.ARCHS:
            List<?> archsList = (List<?>) reader.nextObjectOrNull();
            if (archsList != null) {
              Object[] archsArray = new String[archsList.size()];
              archsList.toArray(archsArray);
              device.archs = (String[]) archsArray;
            }
            break;
          case JsonKeys.BATTERY_LEVEL:
            device.batteryLevel = reader.nextFloatOrNull();
            break;
          case JsonKeys.CHARGING:
            device.charging = reader.nextBooleanOrNull();
            break;
          case JsonKeys.ONLINE:
            device.online = reader.nextBooleanOrNull();
            break;
          case JsonKeys.ORIENTATION:
            device.orientation = reader.nextOrNull(logger, new DeviceOrientation.Deserializer());
            break;
          case JsonKeys.SIMULATOR:
            device.simulator = reader.nextBooleanOrNull();
            break;
          case JsonKeys.MEMORY_SIZE:
            device.memorySize = reader.nextLongOrNull();
            break;
          case JsonKeys.FREE_MEMORY:
            device.freeMemory = reader.nextLongOrNull();
            break;
          case JsonKeys.USABLE_MEMORY:
            device.usableMemory = reader.nextLongOrNull();
            break;
          case JsonKeys.LOW_MEMORY:
            device.lowMemory = reader.nextBooleanOrNull();
            break;
          case JsonKeys.STORAGE_SIZE:
            device.storageSize = reader.nextLongOrNull();
            break;
          case JsonKeys.FREE_STORAGE:
            device.freeStorage = reader.nextLongOrNull();
            break;
          case JsonKeys.EXTERNAL_STORAGE_SIZE:
            device.externalStorageSize = reader.nextLongOrNull();
            break;
          case JsonKeys.EXTERNAL_FREE_STORAGE:
            device.externalFreeStorage = reader.nextLongOrNull();
            break;
          case JsonKeys.SCREEN_WIDTH_PIXELS:
            device.screenWidthPixels = reader.nextIntegerOrNull();
            break;
          case JsonKeys.SCREEN_HEIGHT_PIXELS:
            device.screenHeightPixels = reader.nextIntegerOrNull();
            break;
          case JsonKeys.SCREEN_DENSITY:
            device.screenDensity = reader.nextFloatOrNull();
            break;
          case JsonKeys.SCREEN_DPI:
            device.screenDpi = reader.nextIntegerOrNull();
            break;
          case JsonKeys.BOOT_TIME:
            if (reader.peek() == JsonToken.STRING) {
              device.bootTime = reader.nextDateOrNull(logger);
            }
            break;
          case JsonKeys.TIMEZONE:
            device.timezone = reader.nextTimeZoneOrNull(logger);
            break;
          case JsonKeys.ID:
            device.id = reader.nextStringOrNull();
            break;
          case JsonKeys.CONNECTION_TYPE:
            device.connectionType = reader.nextStringOrNull();
            break;
          case JsonKeys.BATTERY_TEMPERATURE:
            device.batteryTemperature = reader.nextFloatOrNull();
            break;
          case JsonKeys.LOCALE:
            device.locale = reader.nextStringOrNull();
            break;
          case JsonKeys.PROCESSOR_COUNT:
            device.processorCount = reader.nextIntegerOrNull();
            break;
          case JsonKeys.PROCESSOR_FREQUENCY:
            device.processorFrequency = reader.nextDoubleOrNull();
            break;
          case JsonKeys.CPU_DESCRIPTION:
            device.cpuDescription = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      device.setUnknown(unknown);
      reader.endObject();
      return device;
    }
  }

  // endregion
}
